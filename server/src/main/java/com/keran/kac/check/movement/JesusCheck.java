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
 * 水上行走检测(Jesus)：身体浸没在液体中且垂直位移≈0 持续过久。
 *
 * <p><b>v3.0 误报修复</b>：气泡柱（可让人停在水中）、细雪、蜘蛛网、脚手架、
 * 船/载具、被击退全部豁免；要求连续帧验证。
 */
public class JesusCheck extends Check {

    public JesusCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.JESUS);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.liquidTicks = 0;
            resetEvidence(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        // 气泡柱会合法地让玩家在水中停住
        if (p.isSwimming() || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInWeb(p)
                || MoveUtil.isInPowderSnow(p) || MoveUtil.isOnScaffolding(p)) {
            data.liquidTicks = 0;
            resetEvidence(data);
            return;
        }

        // 眼睛位置浸在水中才算深水
        boolean eyeInLiquid = MoveUtil.isLiquid(e.getTo().clone().add(0, 1.4, 0).getBlock())
                || MoveUtil.isLiquid(e.getTo().clone().add(0, 1.6, 0).getBlock());
        boolean bodyInLiquid = MoveUtil.isInLiquid(p);

        if (!eyeInLiquid || !bodyInLiquid) {
            data.liquidTicks = 0;
            resetEvidence(data);
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        double dxz = Math.hypot(e.getTo().getX() - e.getFrom().getX(),
                e.getTo().getZ() - e.getFrom().getZ());
        // 真正的水上行走：垂直不动且水平移动（在水面上平移）
        if (Math.abs(dy) < 0.001 && dxz > 0.02) {
            data.liquidTicks++;
            double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                    * plugin.getConfigManager().getLagCompensationRatio();
            int maxTicks = (int) Math.round(
                    plugin.getConfigManager().getCheckInt(type, "max-liquid-ticks", 50) * lagFactor);
            if (data.liquidTicks > maxTicks) {
                double severity = 1.4;
                if (observe(data, severity)) {
                    flag(data, "液体中水平行走悬浮 " + data.liquidTicks + " ticks", severity);
                }
                data.liquidTicks = 0;
            }
        } else {
            data.liquidTicks = 0;
            decay(data);
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE;
    }
}
