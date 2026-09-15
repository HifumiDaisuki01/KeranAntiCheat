package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

/**
 * 自动连点检测(AutoClicker)：CPS 统计 + <b>点击间隔方差分析</b>。
 *
 * <p><b>v3.0 误报修复</b>（老版只会数 CPS，枪械服不可用）：
 * <ul>
 *   <li>老版单纯用 {@code CPS > 18}，无法区分连点器与<b>高 CPS 合法玩家</b>
 *       （Butterfly/Drag Click 可达 20+）。</li>
 *   <li>新版核心判据是<b>点击间隔的规律性</b>：连点器产生近乎恒定的间隔
 *       （方差极小），而人类点击间隔必然抖动很大。这是区分二者的关键特征。</li>
 *   <li>同时保留 CPS 硬上限作为辅助判据（可配置得很宽松）。</li>
 * </ul>
 */
public class AutoClickerCheck extends Check {

    public AutoClickerCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AUTOCLICKER);
    }

    @Override
    public void onInteract(PlayerInteractEvent e, PlayerData data) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        long now = System.currentTimeMillis();

        // ---------- 记录点击间隔 ----------
        if (data.lastAttackTime > 0) {
            long interval = now - data.lastAttackTime;
            if (interval > 0 && interval < 1000) {
                data.attackIntervals.addLast(interval);
                while (data.attackIntervals.size() > 30) {
                    data.attackIntervals.removeFirst();
                }
            }
        }
        data.lastAttackTime = now;

        // ---------- CPS 窗口统计 ----------
        if (data.cpsClicks == 0) {
            data.cpsWindowStart = now;
        }
        data.cpsClicks++;
        if (now - data.cpsWindowStart >= 1000) {
            int cps = data.cpsClicks;
            data.cpsClicks = 0;
            data.cpsWindowStart = now;

            int maxCps = plugin.getConfigManager().getCheckInt(type, "max-cps", 22);
            if (cps > maxCps) {
                double over = (double) (cps - maxCps) / maxCps;
                double severity = Math.min(2.5, 0.9 + over * 2.0);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "点击频率 %d CPS (上限 %d)", cps, maxCps), severity);
                }
            }
        }

        // ---------- 间隔规律性分析（核心判据） ----------
        int minSamples = plugin.getConfigManager().getCheckInt(type, "min-samples", 15);
        if (data.attackIntervals.size() >= minSamples) {
            double mean = mean(data.attackIntervals);
            double stdev = stdev(data.attackIntervals);
            // 只分析"快速点击"场景（间隔 < 300ms 才有连点器意义）
            if (mean > 20 && mean < 300) {
                double cv = stdev / mean; // 变异系数
                double minCv = plugin.getConfigManager().getCheckDouble(type, "min-cv", 0.06);
                if (cv < minCv) {
                    double severity = Math.min(3.0, 1.5 + (minCv - cv) * 10);
                    if (observe(data, severity)) {
                        flag(data, String.format(Locale.ROOT,
                                "点击间隔过于规律 (均值 %.0fms, 变异系数 %.4f, 疑似连点器)",
                                mean, cv), severity);
                    }
                    data.attackIntervals.clear();
                    return;
                }
            }
            // 样本足够且正常 → 衰减
            decay(data);
        }
    }

    private double mean(java.util.Deque<Long> d) {
        double s = 0;
        for (long v : d) {
            s += v;
        }
        return d.isEmpty() ? 0 : s / d.size();
    }

    private double stdev(java.util.Deque<Long> d) {
        if (d.size() < 2) {
            return 0;
        }
        double m = mean(d);
        double s = 0;
        for (long v : d) {
            s += (v - m) * (v - m);
        }
        return Math.sqrt(s / (d.size() - 1));
    }
}
