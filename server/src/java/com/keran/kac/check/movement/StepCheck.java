package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 爬台阶检测(Step)：单 tick 垂直上升高度超过合法台阶高度（0.6 格）。
 *
 * <p>这是<b>新增检测</b>。MC 正常只能自动跨过 0.6 格高（半砖/台阶），
 * Step 类作弊（如 NCP Step）可一次跨过 1.0~1.5 格甚至更高。
 * <ul>
 *   <li>豁免：跳跃（跳跃时上升幅度大是正常的）、被击退、传送、
 *       液体、气泡柱、蜂蜜/粘液、台阶方块附近、鞘翅。</li>
 *   <li>关键区分：Step 的特征是<b>"没有跳跃却跨过了高方块"</b>，
 *       因此用 {@code isOnGround} 与垂直速度共同判断。</li>
 * </ul>
 */
public class StepCheck extends Check {

    public StepCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.STEP);
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
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInBubbleColumn(p)
                || MoveUtil.hasLevitation(p) || MoveUtil.hasJumpBoost(p)
                || p.isGliding() || MoveUtil.isInWeb(p)
                || MoveUtil.isOnScaffolding(p)) {
            resetEvidence(data);
            return;
        }
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
            resetEvidence(data);
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        double dxz = Math.hypot(e.getTo().getX() - e.getFrom().getX(),
                e.getTo().getZ() - e.getFrom().getZ());

        // Step 特征：垂直上升明显但水平移动很小（跨方块），且不是跳跃
        // 跳跃时的上升通常伴随 dy 在 0.4 左右且是"离开地面"的过程
        boolean playerJumped = data.sinceJumpTicks <= 2;
        double maxStep = plugin.getConfigManager().getCheckDouble(type, "max-step-height", 0.62);

        if (dy > maxStep && !playerJumped && dxz < 0.5 && MoveUtil.isOnGround(p)) {
            // 落地状态下单 tick 上升超限 = 异常跨方块
            data.lastStepHeight = dy;
            double over = (dy - maxStep) / maxStep;
            double severity = Math.min(2.5, 1.0 + over * 2.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "单 tick 上升 %.3f 格 (合法台阶上限 %.2f)", dy, maxStep), severity);
            }
        } else {
            decay(data);
        }
    }
}
