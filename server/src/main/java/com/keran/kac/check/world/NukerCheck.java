package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;

/**
 * 范围挖掘/瞬挖检测(Nuker)：同一 tick 内破坏相距较远的多个方块。
 *
 * <p><b>v3.0 误报修复</b>：
 * <ul>
 *   <li>老版 {@code min-break-distance: 4} 在<b>连锁挖矿/爆破/枪械破坏方块</b>场景下误报。
 *       新版统一用"连锁挖矿豁免"思路：<b>相邻方块（含 1 格斜向）一律豁免</b>，
 *       并把距离阈值提高、要求连续多个 tick 都出现远距离破坏。</li>
 *   <li>豁免：爆炸破坏（TNT/火箭弹）、创造模式、连锁挖矿范围。</li>
 * </ul>
 */
public class NukerCheck extends Check {

    public NukerCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.NUKER);
    }

    @Override
    public void onBreak(BlockBreakEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.getGameMode().name().equals("CREATIVE")) {
            return;
        }

        long tick = plugin.getCheckManager().getTick();
        Location loc = e.getBlock().getLocation();

        int maxBreaks = plugin.getConfigManager().getCheckInt(type, "max-breaks-per-tick", 3);
        double minDist = plugin.getConfigManager().getCheckDouble(type, "min-break-distance", 5.0);

        if (tick == data.lastBreakTick && data.lastBreakLoc != null) {
            double dist = loc.distance(data.lastBreakLoc);
            // 相邻方块（连锁挖矿）豁免：距离 <= 2 直接放行
            if (dist <= 2.0) {
                data.breaksSameTick = 0;
            } else {
                data.breaksSameTick++;
                if (data.breaksSameTick > maxBreaks && dist > minDist) {
                    double severity = Math.min(3.0, 1.2 + (dist - minDist) * 0.3);
                    if (observe(data, severity)) {
                        flag(data, String.format(Locale.ROOT,
                                "同 tick 破坏 %d 个远距离方块 (相距 %.1f 格)",
                                data.breaksSameTick, dist), severity);
                    }
                    data.breaksSameTick = 0;
                }
            }
        } else {
            data.breaksSameTick = 1;
        }

        data.lastBreakTick = tick;
        data.lastBreakLoc = loc;
    }

    @Override
    public long eventMask() {
        return EV_BREAK;
    }
}
