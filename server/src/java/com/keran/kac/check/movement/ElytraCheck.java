package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 鞘翅加速检测：鞘翅滑翔时水平速度超限。
 * 仅对正处于鞘翅滑翔状态的玩家生效。
 */
public class ElytraCheck extends Check {

    public ElytraCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.ELYTRA);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (!p.isGliding() || p.isDead()) {
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.hypot(dx, dz);

        double max = plugin.getConfigManager().getCheckDouble(type, "max-elytra-speed", 3.2);
        if (horizontal > max) {
            flag(data, "鞘翅水平速度 " + trim(horizontal) + " 格/tick", 1);
        }
    }
}
