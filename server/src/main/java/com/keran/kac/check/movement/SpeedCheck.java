package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 速度检测：地面/空中水平速度超限。
 *
 * <p><b>v3.0 误报修复</b>：
 * <ul>
 *   <li>完整考虑加成来源：速度药水、疾跑、跳跃提升（跳跃瞬间会获得额外水平加速）、
 *       击退、下坡、冰面/蓝冰滑行缓冲、气泡柱、蜂蜜/粘液弹射、被爆炸推动。</li>
 *   <li>加入<b>延迟补偿</b>，高 ping 玩家阈值自动放宽。</li>
 *   <li>引入连续帧验证 + 证据累积，避免网络抖动导致的单帧速度尖峰误报。</li>
 *   <li>伤害后一定 tick 内整体豁免（枪械服的击退/后坐力会显著提速）。</li>
 * </ul>
 */
public class SpeedCheck extends Check {

    public SpeedCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.SPEED);
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
        // 环境豁免
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInWeb(p)
                || MoveUtil.isOnScaffolding(p) || MoveUtil.isInPowderSnow(p)
                || p.isGliding() || p.isSwimming() || MoveUtil.hasLevitation(p)) {
            resetEvidence(data);
            return;
        }
        // 受伤/被击退后短窗口豁免
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
            resetEvidence(data);
            return;
        }
        // 冰面滑行：给一段缓冲，不要在离开冰面的瞬间立刻判定
        if (MoveUtil.isOnIce(p)) {
            data.iceTicks = plugin.getConfigManager().getIceBufferTicks();
            resetEvidence(data);
            return;
        }
        if (data.iceTicks > 0) {
            data.iceTicks--;
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 0.001) {
            decay(data);
            return;
        }
        data.lastHorizSpeed = horizontal;

        // ---------- 加成系数计算 ----------
        // 速度药水: 每级 +20%
        int speedLvl = Math.max(0, MoveUtil.getSpeedPotionLevel(p));
        double potionFactor = 1 + 0.2 * speedLvl;
        // 跳跃提升: 起跳瞬间会有额外水平助推，给额外余量
        int jumpLvl = MoveUtil.getJumpBoostLevel(p);
        double jumpFactor = 1 + 0.18 * jumpLvl;
        // 减速类效果反而会降低速度，不需要放宽
        double resistFactor = Math.min(1.6, 1 + MoveUtil.getKnockbackResistance(p) * 0.8);

        double factor = potionFactor * jumpFactor * resistFactor;
        // 延迟补偿
        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();

        boolean onGround = MoveUtil.isOnGround(p);

        if (onGround) {
            double max = plugin.getConfigManager().getCheckDouble(type, "max-ground-speed", 0.72)
                    * factor * lagFactor;
            if (horizontal > max) {
                // 超出比例决定证据权重
                double over = (horizontal - max) / max;
                double severity = Math.min(3.0, 0.8 + over * 3.0);
                if (observe(data, severity)) {
                    flag(data, String.format("地面速度 %.3f 格/tick (上限 %.3f)",
                            horizontal, max), severity);
                }
            } else {
                decay(data);
            }
        } else {
            // 空中：跳跃过程中水平速度本就会高于地面，给更大余量
            double max = plugin.getConfigManager().getCheckDouble(type, "max-air-speed", 1.05)
                    * factor * lagFactor;
            if (horizontal > max) {
                double over = (horizontal - max) / max;
                double severity = Math.min(3.0, 0.8 + over * 3.0);
                if (observe(data, severity)) {
                    flag(data, String.format("空中速度 %.3f 格/tick (上限 %.3f)",
                            horizontal, max), severity);
                }
            } else {
                decay(data);
            }
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE;
    }
}
