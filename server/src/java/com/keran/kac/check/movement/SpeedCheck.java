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
 * 自动豁免：飞行、载具、液体、冰面滑行、速度药水加成、击退、传送。
 */
public class SpeedCheck extends Check {

    public SpeedCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.SPEED);
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
        if (MoveUtil.isInLiquid(p)) {
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 0.001) {
            return;
        }
        data.lastHorizSpeed = horizontal;

        // 速度药水加成: 每级 +20%
        int lvl = Math.max(0, MoveUtil.getSpeedPotionLevel(p));
        double factor = 1 + 0.2 * lvl;

        if (MoveUtil.isOnGround(p)) {
            if (MoveUtil.isOnIce(p)) {
                return;
            }
            double max = plugin.getConfigManager().getCheckDouble(type, "max-ground-speed", 0.65) * factor;
            if (horizontal > max) {
                flag(data, "地面速度 " + trim(horizontal) + " 格/tick (上限 " + trim(max) + ")", 1);
            }
        } else {
            double max = plugin.getConfigManager().getCheckDouble(type, "max-air-speed", 0.85) * factor;
            if (horizontal > max) {
                flag(data, "空中速度 " + trim(horizontal) + " 格/tick (上限 " + trim(max) + ")", 1);
            }
        }
    }
}
