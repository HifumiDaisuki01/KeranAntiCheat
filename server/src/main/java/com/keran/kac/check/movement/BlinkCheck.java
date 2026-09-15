package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;

/**
 * 瞬移检测(Blink/大位移)。
 *
 * <p><b>v3.1 结构性修复 (KAC-05)</b>：v3.0 的实现只检查"单次移动事件位移 &gt; 3.0 格"，
 * 而 Paper 内置的 {@code moved too quickly!} 校验（约 100 格/秒，即 ~5 格/tick）
 * <b>先于插件生效且是丢弃式</b>——超限的那一包根本不会到达插件，
 * 插件能看见的位移上限被硬性截断在 Paper 阈值之下。
 * 于是 v3.0 的 BlinkCheck 落入双重真空区：
 * <ul>
 *   <li><b>超过 3.0 格</b>：绝大部分被 Paper 提前丢弃，插件看不到；</li>
 *   <li><b>低于 3.0 格</b>：不满足 {@code dist &gt; max}，走 decay 分支，永不计违规。</li>
 * </ul>
 * 实测 33 次 flag 全部来自 speed，blink 一次未触发。
 *
 * <p>v3.1 改为<b>速度口径</b>而非"单事件位移口径"：
 * <ol>
 *   <li><b>按 tick 间隔归一位移</b>：计算 {@code dist / elapsedTicks}（格/tick）。
 *       即使位移被 Paper 截断到 4 格，只要发生在 1 tick 内，
 *       归一速度依然是 4.0 格/tick，远高于人类上限（约 0.4 格/tick），可正常命中。</li>
 *   <li><b>阈值下调</b>：{@code max-distance} 默认 3.0 → 1.6 格/tick
 *       （人类疾跑 0.28、跳跃加速 ≈0.4、鞘翅 ≈0.7，1.6 仍有 4 倍余量）。</li>
 *   <li><b>传送事件辅助判定</b>：监听 {@code PlayerTeleportEvent}，对非预期来源的
 *       同世界大距离瞬移累积证据，覆盖"作弊被 Paper 转成服务器侧传送"的情形。</li>
 *   <li><b>环境豁免保留并加强</b>：末影珍珠、紫颂果、传送门、爆炸推动、载具、鞘翅、
 *       液体、蛛网全部豁免，并要求连续 {@code min-consecutive} 次才计违规。</li>
 * </ol>
 */
public class BlinkCheck extends Check {

    /** 人类理论上限（格/tick）：跳跃加速约 0.4，鞘翅约 0.7，取 1.6 留 4 倍余量 */
    private static final double HUMAN_TICK_LIMIT = 1.6;

    public BlinkCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.BLINK);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            resetEvidence(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        // 刚受到伤害（爆炸/远程击退可造成大位移）
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
            decay(data);
            return;
        }
        // 鞘翅/游泳/液体/气泡柱/蛛网/攀爬本身位移大
        if (p.isGliding() || p.isSwimming() || MoveUtil.isInLiquid(p)
                || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInWeb(p)) {
            resetEvidence(data);
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001) {
            decay(data);
            return;
        }

        // ---------- 关键修复: 归一化为"格/tick" ----------
        // 两个移动事件之间可能隔了若干 tick（玩家静止时不产生事件、网络抖动会合包）。
        // 用位移除以间隔 tick 数，还原真实的每 tick 速度。
        long nowTick = plugin.getCheckManager().getTick();
        long gap;
        if (data.lastBlinkTick <= 0 || nowTick <= data.lastBlinkTick) {
            gap = 1;
        } else {
            gap = nowTick - data.lastBlinkTick;
        }
        data.lastBlinkTick = nowTick;
        // 间隔过大（>10 tick）说明中间有长时间静止或无事件时段，分摊后不具参考价值
        if (gap > 10) {
            decay(data);
            return;
        }
        double perTick = dist / gap;
        double verticalPerTick = Math.abs(dy) / gap;

        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();
        // 基础阈值可由配置覆盖，未配置则用 1.6 格/tick
        double base = plugin.getConfigManager().getCheckDouble(type, "max-distance", HUMAN_TICK_LIMIT);
        // 兼容旧配置: 若管理员仍写着 >=3.0 的"单事件位移"口径，自动折算到 per-tick 口径
        if (base >= 3.0) {
            base = HUMAN_TICK_LIMIT;
        }
        double max = base * lagFactor;

        if (perTick > max) {
            double over = (perTick - max) / max;
            double severity = Math.min(3.0, 1.1 + over * 1.8);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "异常位移 %.2f 格/%dtick (%.2f 格/tick, 上限 %.2f%s)",
                        dist, gap, perTick, max,
                        verticalPerTick > max * 0.8 ? ", 垂直分量异常" : ""), severity);
            }
        } else {
            decay(data);
        }
    }

    /**
     * 传送事件辅助判定。
     *
     * <p>Paper 丢弃超速包后，某些瞬移会以"服务器侧传送"的形式落地，
     * 此处对<b>非预期来源</b>的大距离瞬移累积证据。所有正常来源一律豁免。
     */
    @Override
    public void onTeleport(PlayerTeleportEvent e, PlayerData data) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        // 正常传送来源全部豁免
        switch (cause) {
            case ENDER_PEARL:
            case CHORUS_FRUIT:
            case NETHER_PORTAL:
            case END_PORTAL:
            case END_GATEWAY:
            case SPECTATE:
            case EXIT_BED:
            case COMMAND:
            case PLUGIN:
            case DISMOUNT:
            case UNKNOWN:
                return;
            default:
                break;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        double dist = from.distance(to);
        // 同世界内超过 24 格的"原因不明"瞬移才可疑
        if (dist > plugin.getConfigManager().getCheckDouble(type, "max-teleport-distance", 24.0)) {
            double severity = Math.min(3.0, 1.4 + (dist / 100.0));
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "非预期瞬移 %.1f 格 (来源 %s)", dist, cause), severity);
            }
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE | EV_TELEPORT;
    }
}
