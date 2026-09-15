package com.keran.kac.check;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * 检测器抽象基类（v3.0 置信度模型）。
 *
 * <p>与老模型（v2.x）的核心区别：老模型 {@code if (瞬时值 > 阈值) flag()} 单次超限即计违规，
 * 导致网络抖动、传送边界、药水加成、枪械服特殊机制大量误报。
 *
 * <p>本版引入三重误报抑制：
 * <ol>
 *   <li><b>连续帧验证</b>：要求连续 {@code minConsecutive} 次观测到异常才计违规（瞬态尖峰被丢弃）；</li>
 *   <li><b>置信度累积</b>：每次异常累加证据，每次正常按比例衰减，达到 {@code alertThreshold} 才上报；</li>
 *   <li><b>加权违规值</b>：按异常严重程度给出 0.1~3.0 的不同权重，而非一律 +1。</li>
 * </ol>
 */
public abstract class Check {

    // ==================== 事件类型位掩码 (v3.1, KAC-06) ====================
    public static final long EV_MOVE     = 1L << 0;
    public static final long EV_DAMAGE_DEALT = 1L << 1;
    public static final long EV_DAMAGE_TAKEN = 1L << 2;
    public static final long EV_BREAK    = 1L << 3;
    public static final long EV_PLACE    = 1L << 4;
    public static final long EV_INTERACT = 1L << 5;
    public static final long EV_CONSUME  = 1L << 6;
    public static final long EV_CHAT     = 1L << 7;
    public static final long EV_VELOCITY = 1L << 8;
    public static final long EV_TELEPORT = 1L << 9;
    public static final long EV_TICK     = 1L << 10;

    protected final KeranAntiCheat plugin;
    protected final CheckType type;

    public Check(KeranAntiCheat plugin, CheckType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public CheckType getType() {
        return type;
    }

    public String getId() {
        return type.getId();
    }

    /** 该检测当前是否启用(读取配置) */
    public boolean isEnabled() {
        return plugin.getConfigManager().isCheckEnabled(type);
    }

    // ==================== 误报抑制核心 ====================

    /**
     * 记录一次"异常观测"。返回 true 表示证据足够、应真正上报违规。
     *
     * <p>默认行为（可由子类覆写配置）：
     * <ul>
     *   <li>连续次数未达 min-consecutive → 只累积少量证据，不上报（滤掉瞬态尖峰）</li>
     *   <li>证据分达到 alert-threshold → 上报并清零</li>
     * </ul>
     *
     * @param data     玩家数据
     * @param evidence 本次证据权重（严重程度，建议 0.5~3.0）
     */
    protected final boolean observe(PlayerData data, double evidence) {
        return observe(data, evidence, getMinConsecutive(), getAlertThreshold());
    }

    /**
     * 记录一次"异常观测"（可自定义阈值）。
     *
     * @param data           玩家数据
     * @param evidence       本次证据权重
     * @param minConsecutive 需要连续异常的最小次数
     * @param alertThreshold 触发上报的证据分阈值
     * @return 是否应上报
     */
    protected final boolean observe(PlayerData data, double evidence,
                                    int minConsecutive, double alertThreshold) {
        // 连续帧验证
        data.checkConsecutive.merge(type, 1, Integer::sum);
        if (data.checkConsecutive.getOrDefault(type, 0) < minConsecutive) {
            // 还没形成"连续异常链"，仅累积少量证据，不上报
            data.checkBuffer.merge(type, evidence * 0.2, Double::sum);
            return false;
        }
        // 连续异常成立 → 累积证据
        double buf = data.checkBuffer.merge(type, evidence, Double::sum);
        if (buf >= alertThreshold) {
            data.checkBuffer.put(type, 0.0);
            data.checkConsecutive.put(type, 0);
            return true;
        }
        return false;
    }

    /**
     * 记录一次"正常观测"，按衰减率削减证据与连续计数。
     * 所有检测在正常路径上都应调用，否则证据会永久累积造成误报。
     */
    protected final void decay(PlayerData data) {
        decay(data, 1.0);
    }

    /** 记录一次正常观测，并按倍率衰减（倍率越大衰减越快） */
    protected final void decay(PlayerData data, double factor) {
        double rate = plugin.getConfigManager().getDecayRate();
        double cur = data.checkBuffer.getOrDefault(type, 0.0);
        if (cur > 0) {
            data.checkBuffer.put(type, Math.max(0.0, cur - rate * factor));
        }
        // 连续计数回落（不完全清零，允许 1 次偶发中断不推翻整条链）
        int cons = data.checkConsecutive.getOrDefault(type, 0);
        if (cons > 0) {
            data.checkConsecutive.put(type, Math.max(0, cons - (int) Math.ceil(factor)));
        }
    }

    /** 完全重置该检测在玩家身上的证据状态 */
    protected final void resetEvidence(PlayerData data) {
        data.checkBuffer.remove(type);
        data.checkConsecutive.remove(type);
    }

    /** 当前证据分（用于调试输出） */
    protected final double evidenceOf(PlayerData data) {
        return data.checkBuffer.getOrDefault(type, 0.0);
    }

    // ==================== 阈值读取 ====================

    /** 连续异常最小次数（默认 3，可配置） */
    protected final int getMinConsecutive() {
        return Math.max(1, plugin.getConfigManager().getCheckInt(type, "min-consecutive", 3));
    }

    /** 上报所需证据分（默认 1.5，可配置） */
    protected final double getAlertThreshold() {
        return Math.max(0.1, plugin.getConfigManager().getCheckDouble(type, "alert-threshold", 1.5));
    }

    // ==================== 上报 ====================

    /** 触发违规上报（加违规值、告警、按阶梯处罚），权重由调用方给出 */
    public final void flag(PlayerData data, String info, double weight) {
        plugin.getCheckManager().handleFlag(data, type, info, weight);
    }

    /** 兼容旧签名：权重 1.0 */
    public final void flag(PlayerData data, String info, int addVl) {
        plugin.getCheckManager().handleFlag(data, type, info, addVl);
    }

    /** 快捷方式: 由玩家对象获取 PlayerData 后上报 */
    public final void flag(Player player, String info, double weight) {
        PlayerData data = plugin.getCheckManager().getPlayerData(player);
        if (data != null) {
            flag(data, info, weight);
        }
    }

    // ==================== 豁免工具 ====================

    /** 移动类检测的通用临时豁免(击退中/传送后) */
    protected final boolean isMovementExempt(PlayerData data) {
        return data.velocityExemptTicks > 0 || data.teleportExemptTicks > 0;
    }

    /**
     * 按玩家延迟放宽阈值（延迟补偿）。
     * 高 ping 玩家位置/时间信息滞后，必须给出更大的容忍度，否则必然误报。
     *
     * @param base  基础阈值
     * @param ping  玩家延迟(ms)
     * @param ratio 每 100ms 延迟放宽比例（如 0.15 = 每100ms 放宽 15%）
     * @return 放宽后的阈值
     */
    protected final double lagCompensate(double base, int ping, double ratio) {
        int p = Math.max(0, ping);
        return base * (1.0 + (p / 100.0) * ratio);
    }

    /** 数字格式化(保留2位) */
    protected final String trim(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ==================== 事件能力声明 (v3.1, KAC-06) ====================

    /**
     * 本检测器关心的事件种类位掩码。
     *
     * <p><b>为什么需要它（KAC-06）</b>：v3.0 的事件路由对<b>每一个</b>事件都遍历全部
     * 28 个检测器并逐个调用回调，而 {@link Check} 基类的所有回调默认是空实现。
     * 一场战斗中 {@code PlayerMoveEvent} 每秒产生数十次，每次都要空跑
     * 12/28 个未覆写 {@code onMove} 的检测器，且每个检测器还要调用 4 次
     * {@code hasPermission}/{@code isOp} 做豁免判定——纯属浪费服务器算力。
     *
     * <p>v3.1 由各子类覆写本方法声明自己需要的事件，路由时只遍历真正注册了
     * 该事件的检测器，空转调用从 28 次降到实际需要的 8~11 次。
     *
     * <p>默认返回 0（不订阅任何事件），子类必须显式声明。
     */
    public long eventMask() {
        return 0L;
    }

    // ---- 事件回调(按需覆写) ----

    public void onMove(PlayerMoveEvent e, PlayerData data) {
    }

    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
    }

    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
    }

    public void onBreak(BlockBreakEvent e, PlayerData data) {
    }

    public void onPlace(BlockPlaceEvent e, PlayerData data) {
    }

    public void onInteract(PlayerInteractEvent e, PlayerData data) {
    }

    public void onConsume(PlayerItemConsumeEvent e, PlayerData data) {
    }

    public void onChat(AsyncPlayerChatEvent e, PlayerData data) {
    }

    public void onVelocity(PlayerVelocityEvent e, PlayerData data) {
    }

    public void onTeleport(PlayerTeleportEvent e, PlayerData data) {
    }

    /** 每秒 tick 回调(用于时间窗口类统计), 在同步线程执行 */
    public void onTick() {
    }
}
