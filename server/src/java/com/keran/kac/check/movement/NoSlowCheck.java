package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 无减速检测(NoSlow)：在使用物品（吃/喝/拉弓/举盾/吃东西）时本应有明显减速，
 * 若玩家仍保持接近满速移动，则是 NoSlow 类作弊。
 *
 * <p>这是<b>新增检测</b>。判定要点：
 * <ul>
 *   <li>仅在"确实正在使用物品"时生效（{@code isHandRaised} / 使用中）。</li>
 *   <li>正常使用物品时水平速度会被限制；若持续保持高速则可疑。</li>
 *   <li>豁免：液体、冰面、鞘翅、载具、被击退、速度药水。</li>
 *   <li>要求连续多 tick 持续（真 NoSlow 是持续性的）。</li>
 * </ul>
 */
public class NoSlowCheck extends Check {

    public NoSlowCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.NOSLOW);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.noSlowTicks = 0;
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        // 环境豁免
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInWeb(p)
                || MoveUtil.isOnIce(p) || p.isGliding() || p.isSwimming()
                || MoveUtil.isInPowderSnow(p) || MoveUtil.isOnScaffolding(p)) {
            data.noSlowTicks = 0;
            return;
        }

        // 玩家是否正在使用物品（拉弓/举盾/进食/喝药）
        boolean usingItem;
        try {
            usingItem = p.isHandRaised();
        } catch (Throwable t) {
            usingItem = false;
        }
        if (!usingItem) {
            data.noSlowTicks = 0;
            resetEvidence(data);
            return;
        }
        // 拉弓时允许减速，但弓的减速比吃东西小；这里统一用一个较高的速度阈值
        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.hypot(dx, dz);

        double max = plugin.getConfigManager().getCheckDouble(type, "max-use-speed", 0.42);
        if (horizontal > max) {
            data.noSlowTicks++;
            int required = plugin.getConfigManager().getCheckInt(type, "min-ticks", 8);
            if (data.noSlowTicks >= required) {
                double over = (horizontal - max) / max;
                double severity = Math.min(2.5, 1.0 + over * 2.0);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "使用物品时速度达 %.3f 格/tick (上限 %.3f, 无减速)",
                            horizontal, max), severity);
                }
                data.noSlowTicks = 0;
            }
        } else {
            data.noSlowTicks = 0;
            decay(data);
        }
    }
}
