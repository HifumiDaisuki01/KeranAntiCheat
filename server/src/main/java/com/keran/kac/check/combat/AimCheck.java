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
 * <hr>
 * <h3>v3.1 重大修正（针对 KAC-03 审查报告）</h3>
 *
 * <p>v3.0 的三条判据经实测<b>全部失效</b>（45 秒完美机器式旋转 896 帧 → 0 命中），
 * 本版逐条修正：
 *
 * <table border="1">
 *   <tr><th>判据</th><th>v3.0 问题</th><th>v3.1 方案</th></tr>
 *   <tr>
 *     <td>瞬时突变</td>
 *     <td><b>数学死代码</b>：{@code rotation = max(|wrapAngle(yaw差)|, |pitch差|)}
 *         的上界是 180°，而阈值设为 250°，条件恒为 false。
 *         200 万次随机采样验证 rotation 最大仅 179.999778。</td>
 *     <td>阈值下调至 <b>140°</b>（人类单 tick 甩枪极限约 120~130°），
 *         并保留延迟补偿。</td>
 *   </tr>
 *   <tr>
 *     <td>GCD 网格</td>
 *     <td><b>方向完全相反</b>：浮点欧几里得 GCD 对随机序列会迅速退化到 ~0，
 *         对机械等间距序列反而得到大值。实测真实人类 gcd=0.00010105（误报倾向），
 *         自瞄等间距 gcd=0.5（放行）。</td>
 *     <td>改为 <b>GCD 稳定性分析</b>：真人在多个短窗口内算出的 GCD 应当
 *         <b>稳定收敛于同一量级</b>；自瞄若模拟 GCD 则数值漂移大。
 *         判据改为"GCD 变异系数过大"。</td>
 *   </tr>
 *   <tr>
 *     <td>匀速跟踪</td>
 *     <td>CV 阈值 0.12 过窄，且 {@code clear()} 后需重新累积样本，
 *         窗口极窄导致 5.0°/帧 完美匀速也抓不到。</td>
 *     <td>CV 放宽至 <b>0.18</b>；不再 {@code clear()} 而是滑动窗口；
 *         并要求"持续多窗口命中"才上报。</td>
 *   </tr>
 * </table>
 *
 * <p>另新增 <b>判据4 旋转方向反转率</b>：机器人反复微调修正目标时会产生
 * 高频的左←→右转向，人类瞄准则方向更连贯。
 */
public class AimCheck extends Check {

    /** 单 tick 人类甩枪极限（度）。超过此值视为非人类。 */
    private static final double HUMAN_INSTANT_LIMIT = 140.0;

    /** 滑动窗口长度 */
    private static final int WINDOW = 20;

    /** 子窗口长度（用于 GCD 稳定性分析） */
    private static final int SUB_WINDOW = 6;

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

        double yawDelta = wrapAngle(yaw - data.lastYaw);
        double pitchDelta = pitch - data.lastPitch;   // 保留符号，用于方向分析
        data.lastYaw = yaw;
        data.lastPitch = pitch;

        double rotation = Math.max(Math.abs(yawDelta), Math.abs(pitchDelta));

        // 视角没动 → 正常观测，衰减证据
        if (rotation < 0.01) {
            decay(data);
            return;
        }

        // ---------- 记录历史（滑动窗口） ----------
        data.rotationDeltas.addLast(rotation);
        while (data.rotationDeltas.size() > WINDOW) {
            data.rotationDeltas.removeFirst();
        }
        // 带符号的 yaw 增量，用于方向反转率分析
        data.yawDeltas.addLast(yawDelta);
        while (data.yawDeltas.size() > WINDOW) {
            data.yawDeltas.removeFirst();
        }

        int ping = com.keran.kac.util.MoveUtil.pingOf(p);
        double lagFactor = 1.0 + (Math.max(0, ping) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();

        // ==================== 判据1: 单帧瞬时突变 ====================
        // v3.1 修正: 阈值 250 -> 140。原来 250 超过数学上界 180, 是死代码。
        double instantUser = plugin.getConfigManager().getCheckDouble(type,
                "max-instant-rotation", HUMAN_INSTANT_LIMIT);
        double instantMax = instantUser * lagFactor;
        if (rotation > instantMax) {
            double over = (rotation - instantMax) / instantMax;
            double severity = Math.min(3.0, 1.6 + over * 3.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "单帧视角突变 %.1f° (yaw %.1f / pitch %.1f, 人类极限约 %.0f°)",
                        rotation, yawDelta, pitchDelta, HUMAN_INSTANT_LIMIT), severity);
            }
            data.rotationDeltas.clear();
            return;
        }

        // 样本不足 → 只衰减
        int minSamples = plugin.getConfigManager().getCheckInt(type, "min-samples", 14);
        if (data.rotationDeltas.size() < minSamples) {
            decay(data);
            return;
        }

        // ==================== 判据2: 旋转方向反转率 ====================
        // 机器人修正瞄准时高频左右抖; 人类倾向朝一个方向平滑移动。
        // 仅在"有明显转幅"的帧上统计, 避免微小抖动噪声。
        double reverseRate = computeReverseRate(data.yawDeltas);
        double maxReverse = plugin.getConfigManager().getCheckDouble(type,
                "max-reverse-rate", 0.62);
        double meanRot = mean(data.rotationDeltas);
        if (meanRot > 2.5 && reverseRate > maxReverse) {
            double severity = 1.1;
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "视角方向高频反转 (反转率 %.2f, 均值 %.2f°, 疑似自瞄微调)",
                        reverseRate, meanRot), severity);
            }
            return;
        }

        // ==================== 判据3: GCD 稳定性 ====================
        // v3.1 修正: 不再用 "GCD 过小" (方向反了), 而用 "GCD 在不同子窗口间漂移过大"。
        // 真人鼠标灵敏度固定 => 各单位窗口算出的 GCD 应稳定;
        // 自瞄若人为模拟 GCD, 数值常不稳定; 直接设角则 GCD 退化乱跳。
        int gcdSubWindows = plugin.getConfigManager().getCheckInt(type,
                "gcd-sub-windows", 3);
        double[] subGcds = computeSubWindowGcds(data.rotationDeltas, gcdSubWindows);
        if (subGcds != null) {
            double gcdMean = avg(subGcds);
            double gcdStdev = stdevArr(subGcds, gcdMean);
            double gcdCv = gcdStdev / Math.max(0.0001, gcdMean);
            double maxGcdCv = plugin.getConfigManager().getCheckDouble(type,
                    "max-gcd-cv", 1.10);
            // 仅当均值有意义(不是全部退化到 0)时才判断
            if (gcdMean > 0.0005 && gcdCv > maxGcdCv) {
                double severity = 1.2;
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "视角增量无稳定灵敏度网格 (子窗口 GCD 均值 %.5f, 变异系数 %.2f)",
                            gcdMean, gcdCv), severity);
                }
            }
        }

        // ==================== 判据4: 匀速跟踪 ====================
        // v3.1 修正: CV 阈值 0.12 -> 0.18; 不再 clear() 破坏窗口。
        double stdev = stdev(data.rotationDeltas);
        double mean = mean(data.rotationDeltas);
        double maxAngle = plugin.getConfigManager().getCheckDouble(type,
                "smooth-angle-range", 90);
        double maxCv = plugin.getConfigManager().getCheckDouble(type, "max-rotation-cv", 0.18);
        if (mean > 1.5 && mean < maxAngle && stdev / Math.max(0.0001, mean) < maxCv) {
            double severity = 1.0;
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "视角匀速跟踪 (均值 %.2f°, 波动率 %.3f, 疑似自动瞄准)",
                        mean, stdev / mean), severity);
            }
            return;
        }

        decay(data);
    }

    // ==================== 工具方法 ====================

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

    /**
     * 方向反转率：相邻两个"有意义"的 yaw 增量符号相反的占比。
     * 人类平滑瞄准通常同向连续，机器人修正会高频反向。
     */
    private double computeReverseRate(java.util.Deque<Double> deltas) {
        int flips = 0;
        int compared = 0;
        int prevSign = 0;
        for (double d : deltas) {
            if (Math.abs(d) < 0.8) {
                continue;   // 忽略微小抖动
            }
            int sign = d > 0 ? 1 : -1;
            if (prevSign != 0) {
                compared++;
                if (sign != prevSign) {
                    flips++;
                }
            }
            prevSign = sign;
        }
        return compared == 0 ? 0 : (double) flips / compared;
    }

    /**
     * 把窗口切成 n 个连续子窗口, 分别算 GCD, 返回数组。
     * 样本不足或子窗口过小则返回 null。
     */
    private double[] computeSubWindowGcds(java.util.Deque<Double> deltas, int n) {
        int size = deltas.size();
        if (size < SUB_WINDOW * n) {
            return null;
        }
        double[] out = new double[n];
        Double[] arr = deltas.toArray(new Double[0]);
        int step = size / n;
        for (int i = 0; i < n; i++) {
            int from = i * step;
            int to = (i == n - 1) ? size : from + step;
            out[i] = gcdOfArray(arr, from, to);
        }
        return out;
    }

    /** 对数组 [from, to) 区间计算"网格步长"(GCD) */
    private double gcdOfArray(Double[] arr, int from, int to) {
        double gcd = -1;
        for (int i = from; i < to; i++) {
            double d = arr[i];
            if (d < 0.01) {
                continue;
            }
            gcd = (gcd < 0) ? d : gcdOf(gcd, d);
            if (gcd < 0.0001) {
                return 0;
            }
        }
        return gcd < 0 ? 0 : gcd;
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

    private double avg(double[] a) {
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return a.length == 0 ? 0 : s / a.length;
    }

    private double stdevArr(double[] a, double mean) {
        if (a.length < 2) {
            return 0;
        }
        double s = 0;
        for (double v : a) {
            s += (v - mean) * (v - mean);
        }
        return Math.sqrt(s / (a.length - 1));
    }

    @Override
    public long eventMask() {
        return EV_MOVE;
    }
}
