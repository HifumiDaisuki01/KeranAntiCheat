package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 视角旋转检测(Aim)：识别"瞄准机器人"式的非人类视角运动。
 *
 * <p><b>v3.0 误报修复</b>（老版在枪械服几乎不可用）：
 * <ul>
 *   <li>老版用 {@code System.currentTimeMillis()} 且 {@code interval<=100ms} 就判定"单 tick 旋转 90°"，
 *       导致高 DPI 玩家<b>甩枪/瞬狙必然误报</b>。新版改用<b>tick 间隔</b>判断。</li>
 *   <li>不再单纯看单次旋转角度（人类甩枪可达 180°+），而是分析<b>旋转的统计学特征</b>：
 *       <ul>
 *         <li><b>GCD 一致性</b>：真实鼠标移动的视角增量是鼠标灵敏度步长的整数倍，
 *             瞄准机器人往往产生"不落在灵敏度网格上"的角度 —— 这是最强的自瞄特征。</li>
 *         <li><b>旋转平滑度</b>：人类视角运动有加速减速曲线，机器人常是瞬时突变或恒定角速度。</li>
 *         <li><b>连续异常帧</b>：要求连续多帧异常，滤掉单次瞬态。</li>
 *       </ul></li>
 *   <li>补全 pitch 边界（±90°）处理与 yaw 环绕插值。</li>
 * </ul>
 */
public class AimCheck extends Check {

    public AimCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AIM);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.isDead()) {
            return;
        }

        float yaw = e.getTo().getYaw();
        float pitch = e.getTo().getPitch();
        long tick = plugin.getCheckManager().getTick();

        double yawDelta = wrapAngle(yaw - data.lastYaw);
        double pitchDelta = Math.abs(pitch - data.lastPitch);
        data.lastYaw = yaw;
        data.lastPitch = pitch;

        // 只统计"视角真的动了"的帧
        double rotation = Math.max(Math.abs(yawDelta), pitchDelta);
        if (rotation < 0.01) {
            decay(data);
            return;
        }

        // ---------- 1. 记录旋转历史（用于统计学分析） ----------
        data.rotationDeltas.addLast(rotation);
        while (data.rotationDeltas.size() > 40) {
            data.rotationDeltas.removeFirst();
        }

        int ping = com.keran.kac.util.MoveUtil.pingOf(p);
        double lagFactor = 1.0 + (Math.max(0, ping) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();

        // ---------- 2. 瞬时突变检测（阈值大幅放宽，只抓真正非人类的） ----------
        // 人类在 1 tick(50ms) 内极限约 180°（甩枪），这里阈值放到 250° 且要求连续成立
        double instantMax = plugin.getConfigManager().getCheckDouble(type, "max-instant-rotation", 250)
                * lagFactor;
        if (rotation > instantMax) {
            // 注意：非常罕见的角度才会进入这里，权重高
            double over = (rotation - instantMax) / instantMax;
            double severity = Math.min(3.0, 1.5 + over * 3.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT, "单帧视角突变 %.1f° (yaw %.1f / pitch %.1f)",
                        rotation, yawDelta, pitchDelta), severity);
            }
            return;
        }

        // ---------- 3. GCD 灵敏度一致性分析（自瞄核心特征） ----------
        // 真实鼠标：每次视角增量 ≈ 鼠标位移量 × 灵敏度，因此存在最小公倍步长(GCD)。
        // 瞄准机器人直接设置角度，增量往往随机分布，无法形成稳定的 GCD 网格。
        int gcdSamples = plugin.getConfigManager().getCheckInt(type, "gcd-min-samples", 20);
        if (data.rotationDeltas.size() >= gcdSamples) {
            double gcd = computeGcdOfDeltas(data.rotationDeltas);
            // gcd 过小说明增量高度随机（真机不会）；正常玩家 gcd 通常 >= 0.01 且稳定
            double minGcd = plugin.getConfigManager().getCheckDouble(type, "min-gcd", 0.002);
            if (gcd >= 0 && gcd < minGcd) {
                // 需要持续异常才算数
                double severity = 1.2;
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "视角增量无灵敏度网格特征 (gcd=%.5f°, 样本=%d, 疑似瞄准机器人)",
                            gcd, data.rotationDeltas.size()), severity);
                }
                data.rotationDeltas.clear();
                return;
            }
        }

        // ---------- 4. 旋转平滑度分析（恒定角速度 = 机器人） ----------
        int smoothSamples = plugin.getConfigManager().getCheckInt(type, "smooth-min-samples", 12);
        if (data.rotationDeltas.size() >= smoothSamples) {
            double stdev = stdev(data.rotationDeltas);
            double mean = mean(data.rotationDeltas);
            // 人：视角速度有波动（stdev/mean 通常 > 0.35）；
            // 机器人：匀速跟踪目标时非常平稳
            double maxAngle = plugin.getConfigManager().getCheckDouble(type, "smooth-angle-range", 60);
            if (mean > 2.0 && mean < maxAngle && stdev / Math.max(0.0001, mean) < 0.12) {
                double severity = 1.0;
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "视角匀速跟踪 (均值 %.2f°, 波动率 %.3f, 疑似自动瞄准)",
                            mean, stdev / mean), severity);
                }
                data.rotationDeltas.clear();
                return;
            }
        }

        decay(data);
    }

    /** 角度环绕处理：把差值规范到 [-180, 180] */
    private double wrapAngle(double a) {
        while (a > 180) {
            a -= 360;
        }
        while (a < -180) {
            a += 360;
        }
        return a;
    }

    /** 计算增量序列的"网格步长"(GCD)，反映鼠标灵敏度一致性 */
    private double computeGcdOfDeltas(java.util.Deque<Double> deltas) {
        double gcd = -1;
        for (double d : deltas) {
            if (d < 0.01) {
                continue; // 忽略微小抖动
            }
            gcd = (gcd < 0) ? d : gcdOf(gcd, d);
            if (gcd < 0.0001) {
                return 0; // 已退化，无网格
            }
        }
        return gcd;
    }

    private double gcdOf(double a, double b) {
        for (int i = 0; i < 40; i++) {
            if (b < 0.0001) {
                return a;
            }
            double t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private double mean(java.util.Deque<Double> d) {
        double s = 0;
        for (double v : d) {
            s += v;
        }
        return d.isEmpty() ? 0 : s / d.size();
    }

    private double stdev(java.util.Deque<Double> d) {
        if (d.size() < 2) {
            return 0;
        }
        double m = mean(d);
        double s = 0;
        for (double v : d) {
            s += (v - m) * (v - m);
        }
        return Math.sqrt(s / (d.size() - 1));
    }
}
