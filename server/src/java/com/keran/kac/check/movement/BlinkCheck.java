package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 瞬移检测(Blink/大位移)：单次移动事件位移过大。
 * 自动豁免：传送、末影珍珠、击退、载具。
 */
public class BlinkCheck extends Check {

    public BlinkCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.BLINK);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double max = plugin.getConfigManager().getCheckDouble(type, "max-distance", 2.5);
        if (dist > max) {
            flag(data, "单次位移 " + trim(dist) + " 格", 1);
        }
    }
}
