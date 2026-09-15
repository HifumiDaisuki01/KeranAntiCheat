package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 爬墙检测(Spider)：贴墙持续上升且净高度超过跳跃极限。
 * 自动豁免：攀爬方块、液体、飞行、载具、击退、传送。
 */
public class SpiderCheck extends Check {

    public SpiderCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.SPIDER);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.wallTicks = 0;
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        if (MoveUtil.isClimbing(e.getTo().getBlock())
                || MoveUtil.isClimbing(e.getTo().clone().add(0, -0.1, 0).getBlock())
                || MoveUtil.isInLiquid(p)) {
            data.wallTicks = 0;
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        boolean besideWall = MoveUtil.isBesideWall(p);

        if (dy > 0.01 && besideWall && !MoveUtil.isOnGround(p)) {
            if (data.wallTicks == 0) {
                data.takeoffY = e.getFrom().getY();
            }
            data.wallTicks++;
            int maxTicks = plugin.getConfigManager().getCheckInt(type, "max-wall-ticks", 10);
            if (data.wallTicks > maxTicks && (e.getTo().getY() - data.takeoffY) > 1.3) {
                flag(data, "贴墙上升 " + data.wallTicks + " ticks, 净高 " + trim(e.getTo().getY() - data.takeoffY) + " 格", 1);
                data.wallTicks = 0;
            }
        } else {
            data.wallTicks = 0;
        }
    }
}
