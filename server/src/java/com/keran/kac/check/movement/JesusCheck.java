package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 水上行走检测(Jesus)：身体浸没在液体中且垂直位移≈0 持续过久。
 * 自动豁免：游泳状态、浅水(眼睛不在水中)、击退、传送。
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
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        if (p.isSwimming()) {
            data.liquidTicks = 0;
            return;
        }

        // 眼睛位置(约 +1.62)浸在水中才算深水
        boolean eyeInLiquid = MoveUtil.isLiquid(e.getTo().clone().add(0, 1.4, 0).getBlock())
                || MoveUtil.isLiquid(e.getTo().clone().add(0, 1.6, 0).getBlock());
        boolean bodyInLiquid = MoveUtil.isInLiquid(p);

        if (!eyeInLiquid || !bodyInLiquid) {
            data.liquidTicks = 0;
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        if (Math.abs(dy) < 0.001) {
            data.liquidTicks++;
            int maxTicks = plugin.getConfigManager().getCheckInt(type, "max-liquid-ticks", 40);
            if (data.liquidTicks > maxTicks) {
                flag(data, "液体中悬浮 " + data.liquidTicks + " ticks", 1);
                data.liquidTicks = 0;
            }
        } else {
            data.liquidTicks = 0;
        }
    }
}
