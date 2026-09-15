package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 摔落检测(NoFall)：追踪自由落体段, 若下落距离较大却从未受到摔落伤害则判定异常。
 * 自动豁免：液体、攀爬、缓降、载具、蜘蛛网、粘液块、蜂蜜块、实际摔伤。
 */
public class NoFallCheck extends Check {

    public NoFallCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.NOFALL);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.falling = false;
            return;
        }
        if (MoveUtil.hasSlowFalling(p) || MoveUtil.isInLiquid(p)
                || MoveUtil.isClimbing(e.getTo().getBlock())) {
            data.falling = false;
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();

        if (dy < -0.1) {
            // 正在下降
            if (!data.falling) {
                data.falling = true;
                data.fallStartY = e.getFrom().getY();
            }
        } else if (data.falling) {
            // 下降段结束(落地/停下)
            double fall = data.fallStartY - e.getTo().getY();
            data.falling = false;
            if (fall <= 0) {
                return;
            }
            // 安全着陆物豁免
            Material below = e.getTo().clone().add(0, -0.1, 0).getBlock().getType();
            if (below == Material.COBWEB || below == Material.SLIME_BLOCK
                    || below == Material.HONEY_BLOCK) {
                return;
            }
            double minFall = plugin.getConfigManager().getCheckDouble(type, "min-fall-height", 3.0);
            long now = System.currentTimeMillis();
            if (fall >= minFall && (now - data.lastFallDamageTime) > 1000) {
                flag(data, "下落 " + trim(fall) + " 格未受摔落伤害", 1);
            }
        }
    }

    @Override
    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            data.lastFallDamageTime = System.currentTimeMillis();
        }
    }
}
