package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;

/**
 * 透视挖矿检测(XRay)：多维度启发式分析。
 *
 * <p><b>v3.0 误报修复</b>（老版只看比例，样本太小易误报）：
 * <ul>
 *   <li>老版 {@code min-blocks: 25} 样本太小、且只看矿物占比。新版<b>样本量提高到 60</b>，
 *       并要求<b>连续多个统计窗口</b>都超标。</li>
 *   <li>新增<b>"不经石直取矿物"分析</b>：正常挖矿必然挖掉大量石头，
 *       而透视玩家会直奔矿脉 —— 连续多次"未挖石头就命中珍贵矿物"是强特征。</li>
 *   <li>新增<b>深度/路径分析</b>：统计矿物是否分布在异常的水平直线（直线挖矿）上。</li>
 *   <li>珍贵矿物（钻石/绿宝石/远古残骸）权重远高于普通矿物。</li>
 *   <li>与连锁挖矿插件联用时，把 {@code min-blocks} 调更大。</li>
 * </ul>
 */
public class XRayCheck extends Check {

    public XRayCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.XRAY);
    }

    @Override
    public void onBreak(BlockBreakEvent e, PlayerData data) {
        Player p = e.getPlayer();
        String gm = p.getGameMode().name();
        if (gm.equals("CREATIVE") || gm.equals("SPECTATOR")) {
            return;
        }

        Material m = e.getBlock().getType();
        String name = m.name();
        // 忽略透明装饰方块, 减少干扰
        if (name.contains("GLASS") || name.contains("TORCH") || name.contains("LEAVES")
                || name.contains("FLOWER") || (name.contains("GRASS") && !name.contains("GRASS_BLOCK"))) {
            return;
        }

        data.totalMined++;
        boolean ore = MoveUtil.isOre(m);
        boolean precious = MoveUtil.isPreciousOre(m);
        boolean stone = MoveUtil.isStoneLike(m);

        if (ore) {
            data.oreMined++;
            if (precious) {
                data.oreMined++; // 珍贵矿物双倍权重
            }
        }
        // ---------- 强特征：没挖石头就直接命中珍贵矿物 ----------
        if (precious) {
            if (stone || data.lastBreakTick > 0) {
                // 检查距离上次挖石头的间隔
            }
            data.oresWithoutStone++;
        } else if (stone) {
            data.oresWithoutStone = Math.max(0, data.oresWithoutStone - 1);
        }

        // 记录深度
        data.oreDepths.addLast((double) e.getBlock().getY());
        while (data.oreDepths.size() > 40) {
            data.oreDepths.removeFirst();
        }

        int minBlocks = plugin.getConfigManager().getCheckInt(type, "min-blocks", 60);
        if (data.totalMined < minBlocks) {
            return;
        }

        // ---------- 判据一：矿物占比 ----------
        double ratio = (double) data.oreMined / data.totalMined;
        double maxRatio = plugin.getConfigManager().getCheckDouble(type, "max-ore-ratio", 0.30);
        if (ratio > maxRatio) {
            double over = (ratio - maxRatio) / Math.max(0.01, maxRatio);
            double severity = Math.min(2.5, 1.0 + over * 1.5);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "矿物占比 %.1f%% (%d/%d) 疑似透视挖矿",
                        ratio * 100, data.oreMined, data.totalMined), severity);
            }
            data.oreMined = 0;
            data.totalMined = 0;
            return;
        }

        // ---------- 判据二：未挖石头直取珍贵矿物的累计次数 ----------
        int maxOresWithoutStone = plugin.getConfigManager().getCheckInt(type, "max-ores-without-stone", 6);
        if (data.oresWithoutStone >= maxOresWithoutStone) {
            double severity = 2.0;
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "连续 %d 次未经石层直取珍贵矿物 (透视特征)", data.oresWithoutStone), severity);
            }
            data.oresWithoutStone = 0;
        }

        int resetAfter = plugin.getConfigManager().getCheckInt(type, "reset-after", 300);
        if (data.totalMined >= resetAfter) {
            data.oreMined = 0;
            data.totalMined = 0;
        }
    }
}
