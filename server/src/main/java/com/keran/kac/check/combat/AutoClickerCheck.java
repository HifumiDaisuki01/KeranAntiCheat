package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 *
 * <p><b>v3.1 结构性修复 (KAC-07)</b>：v3.0 只监听
 * {@code PlayerInteractEvent} 的 {@code LEFT_CLICK_AIR/LEFT_CLICK_BLOCK}，
 * 而这个事件在<b>左键挖方块</b>时同样触发（挖方块属于左键交互）。
 * 后果有两个：
 * <ol>
 *   <li><b>挖矿行为被当成攻击计数</b>：连续挖矿（创造模式/效率镐/速挖时 CPS 极高）
 *       会被误判为连点器，这是典型的误报来源；</li>
 *   <li><b>真实攻击漏计</b>：攻击实体后 {@code EntityDamageByEntityEvent} 与
 *       {@code PlayerInteractEvent} 的触发顺序和去重行为并不一致，
 *       单纯依赖 interact 会漏掉部分攻击，使 CPS 与间隔样本失真。</li>
 * </ol>
 * v3.1 改为：<b>攻击间隔样本只从 {@code EntityDamageByEntityEvent} 采集</b>
 * （这才是真正的"点击攻击"语义）；{@code PlayerInteractEvent} 仅在
 * <b>对着空气左键且手中无方块破坏意图</b>时用于补充空挥样本，
 * 且与最近一次攻击时间戳去重（同一 tick 内不重复计数）。
 */
public class AutoClickerCheck extends Check {

    /** 同一个 tick 内 interact 与 damage 会同时到达，用于去重 */
    private static final long DEDUP_MS = 5L;

    public AutoClickerCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AUTOCLICKER);
    }

    /**
     * 真实攻击（命中实体）—— 这是攻击间隔的<b>权威来源</b>。
     */
    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        // 只有对生物的攻击才算"点击攻击"（打方块已由 BlockBreak 覆盖）
        if (!(e.getEntity() instanceof LivingEntity)) {
            return;
        }
        recordClick(data, true);
    }

    /**
     * 左键交互。此处<b>排除挖方块</b>，避免把挖矿 CPS 当成攻击 CPS。
     */
    @Override
    public void onInteract(PlayerInteractEvent e, PlayerData data) {
        Action a = e.getAction();
        // 只考虑左键
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        // 关键修复: 左键"点到方块"通常是挖矿/破坏，不计入攻击 CPS
        // （真正的攻击会走 onDamageDealt）
        boolean mining = a == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null
                && !e.getPlayer().isSneaking();
        if (mining) {
            return;
        }
        recordClick(data, false);
    }

    /**
     * 记录一次点击。
     *
     * @param data    玩家数据
     * @param isAttack 是否来自真实攻击事件（权威来源）
     */
    private void recordClick(PlayerData data, boolean isAttack) {
        long now = System.currentTimeMillis();

        // 去重: 同一时刻的 interact + damage 双事件只计一次
        if (now - data.lastAttackTime < DEDUP_MS) {
            return;
        }

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

        // 只有真实攻击才推进 CPS 窗口，避免空挥/挖矿污染 CPS
        if (!isAttack) {
            return;
        }

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
                            "攻击频率 %d CPS (上限 %d)", cps, maxCps), severity);
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
                                "攻击间隔过于规律 (均值 %.0fms, 变异系数 %.4f, 疑似连点器)",
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

    @Override
    public long eventMask() {
        // v3.1: 同时订阅真实攻击事件，攻击 CPS 以此为准
        return EV_DAMAGE_DEALT | EV_INTERACT;
    }
}

