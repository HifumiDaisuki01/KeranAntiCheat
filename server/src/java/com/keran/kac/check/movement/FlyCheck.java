package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 飞行检测：持续上升 / 超出跳跃高度 / 空中悬浮。
 *
 * <p><b>v3.0 误报修复</b>（老版是误报重灾区）：
 * <ul>
 *   <li>豁免矩阵大幅补全：气泡柱、蜘蛛网、脚手架、细雪、蜂蜜、船/矿车、跳跃提升药水、
 *       潜影贝漂浮、被方块挤压、鞘翅、梯子/藤蔓，以及"最近受伤/被击退"状态。</li>
 *   <li>悬浮检测不再只看 {@code |dy| ≈ 0}（贴墙卡住、站在栅栏边缘都满足），
 *       改为要求垂直与水平位移都接近 0，且不贴墙、不在方块边缘、附近无支撑。</li>
 *   <li>持续上升要求<b>净升高</b>超出合法跳跃能力，而非单纯累计上升 tick。</li>
 *   <li>引入连续帧验证 + 证据累积，单次瞬态不再计违规。</li>
 * </ul>
 */
public class FlyCheck extends Check {

    public FlyCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.FLY);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();

        // ---------- 硬性豁免：完全重置，不产生任何证据 ----------
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            resetAll(data);
            return;
        }
        // 服务端传送/击退后的宽容窗口
        if (isMovementExempt(data)) {
            resetAll(data);
            return;
        }
        // 环境豁免：这些机制会造成合法的"滞空/上升/悬浮"
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInWeb(p)
                || MoveUtil.isClimbing(e.getTo().getBlock())
                || MoveUtil.isClimbing(e.getTo().clone().add(0, -0.1, 0).getBlock())
                || MoveUtil.isOnScaffolding(p) || MoveUtil.isInPowderSnow(p)
                || MoveUtil.isOnHoneyOrSlime(p) || p.isGliding() || p.isSwimming()
                || MoveUtil.hasLevitation(p) || MoveUtil.hasSlowFalling(p)
                || MoveUtil.hasJumpBoost(p)) {
            resetAll(data);
            return;
        }
        // 最近受伤（被击退/爆炸推动）后的短窗口内，上升是合法的
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
            resetAll(data);
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();
        double dy = to.getY() - from.getY();
        double dxz = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());

        // ---------- 在地面：重置滞空状态 ----------
        if (MoveUtil.isOnGround(p)) {
            resetAll(data);
            data.lastGroundTick = plugin.getCheckManager().getTick();
            return;
        }

        data.airTicks++;
        // 延迟补偿：高 ping 玩家的阈值整体放宽
        int ping = MoveUtil.pingOf(p);
        double lagFactor = 1.0 + (Math.max(0, ping) / 100.0) * plugin.getConfigManager().getLagCompensationRatio();

        // ================= 检查一：持续上升（要求净升高超限） =================
        if (dy > 0.01) {
            data.ascendTicks++;
            if (!data.inAirSince) {
                data.inAirSince = true;
                data.takeoffY = from.getY();
            }
        } else {
            data.ascendTicks = Math.max(0, data.ascendTicks - 2);
        }

        int maxAscendTicks = (int) Math.round(
                plugin.getConfigManager().getCheckInt(type, "max-ascend-ticks", 26) * lagFactor);
        // 净升高 = 当前高度 - 起跳高度；正常跳跃最高约 1.25 格
        double netRise = data.inAirSince ? (to.getY() - data.takeoffY) : 0;
        double maxNetRise = plugin.getConfigManager().getCheckDouble(type, "max-net-rise", 2.2);

        if (data.inAirSince && data.ascendTicks > maxAscendTicks
                && netRise > maxNetRise && dy > 0.005) {
            // 证据权重随超出程度递增：超出越多越像飞行
            double severity = Math.min(3.0, 1.0 + (netRise - maxNetRise) * 0.5);
            if (observe(data, severity)) {
                flag(data, String.format("持续上升 %d ticks 净升高 %.2f 格 (dy=%.3f)",
                        data.ascendTicks, netRise, dy), severity);
            }
            // 只削减计数，不完全清零（避免一帧真实上升就洗白）
            data.ascendTicks = maxAscendTicks / 2;
        } else {
            decay(data);
        }

        // ================= 检查二：悬停高度（跳跃无法达到的高度） =================
        if (data.inAirSince && data.airTicks > 14 && netRise > maxNetRise * 1.4
                && Math.abs(dy) < 0.08 && dxz < 0.30) {
            // 空中停在高处且几乎不动 —— 飞行特征
            double severity = Math.min(3.0, 1.4 + (netRise - maxNetRise) * 0.4);
            if (observe(data, severity)) {
                flag(data, String.format("空中悬停于起跳点上方 %.2f 格 (dy=%.3f)", netRise, dy), severity);
            }
        }

        // ================= 检查三：纯净悬浮（严格化，防贴墙/卡位误报） =================
        // 老版仅判断 |dy|<0.001，导致贴墙卡住、站在栅栏边缘全部误报。
        // 新版要求：垂直位移≈0 且 水平位移≈0 且 不贴墙 且 不在方块边缘 且 附近无支撑。
        boolean pureHover = Math.abs(dy) < 0.002 && dxz < 0.02
                && !MoveUtil.isBesideWall(p) && !MoveUtil.isOnBlockEdge(p)
                && !MoveUtil.hasNearbySolidSupport(p);
        if (pureHover) {
            data.hoverTicks++;
            int maxHover = (int) Math.round(
                    plugin.getConfigManager().getCheckInt(type, "max-hover-ticks", 60) * lagFactor);
            if (data.hoverTicks > maxHover) {
                double severity = Math.min(2.5, 1.0 + (data.hoverTicks - maxHover) / 40.0);
                if (observe(data, severity)) {
                    flag(data, "空中完全静止悬浮 " + data.hoverTicks + " ticks", severity);
                }
                data.hoverTicks = maxHover / 2;
            } else {
                // 未达阈值：轻微累积证据但不报警
                observe(data, 0.05);
            }
        } else {
            data.hoverTicks = 0;
            decay(data);
        }
    }

    private void resetAll(PlayerData data) {
        data.airTicks = 0;
        data.ascendTicks = 0;
        data.hoverTicks = 0;
        data.inAirSince = false;
        // 注意：这里刻意不 resetEvidence，让证据按正常衰减回落，
        // 避免"被击退一下就洗白全部证据"的漏洞。
        decay(data);
    }
}
