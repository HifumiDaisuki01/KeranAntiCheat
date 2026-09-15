package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Locale;

/**
 * 快速食用检测(FastUse)：每秒食用物品次数超限。
 * 正常食用有 1.6 秒进食动画, 作弊可瞬间多次。
 *
 * <p><b>v3.0 误报修复</b>：老版上限 4/s，但<b>枪械服里"吃药/治疗道具"经插件加速后
 * 频率可能更高</b>。新版改为基于<b>进食动画时间</b>的物理校验：两次成功进食之间的
 * 间隔不应短于 {@code min-interval-ms}（默认 400ms），比单纯数次数更严谨且更难误报。
 */
public class FastUseCheck extends Check {

    public FastUseCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.FASTUSE);
    }

    @Override
    public void onConsume(PlayerItemConsumeEvent e, PlayerData data) {
        long now = System.currentTimeMillis();

        // ---------- 基于最小间隔的物理校验（核心判据） ----------
        long minInterval = plugin.getConfigManager().getCheckInt(type, "min-interval-ms", 400);
        if (data.lastConsumeTime > 0) {
            long interval = now - data.lastConsumeTime;
            if (interval < minInterval) {
                double severity = Math.min(3.0, 1.2 + (double) (minInterval - interval) / minInterval);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "进食间隔仅 %d ms (最小 %d ms)", interval, minInterval), severity);
                }
            } else {
                decay(data);
            }
        }
        data.lastConsumeTime = now;

        // ---------- 辅助判据：每秒次数 ----------
        if (data.consumeWindowStart == 0) {
            data.consumeWindowStart = now;
        }
        data.consumesThisSecond++;
        if (now - data.consumeWindowStart >= 1000) {
            int uses = data.consumesThisSecond;
            data.consumesThisSecond = 0;
            data.consumeWindowStart = now;
            int maxUses = plugin.getConfigManager().getCheckInt(type, "max-uses-per-second", 5);
            if (uses > maxUses) {
                double severity = 1.5;
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "每秒食用 %d 次 (上限 %d)", uses, maxUses), severity);
                }
            }
        }
    }
}
