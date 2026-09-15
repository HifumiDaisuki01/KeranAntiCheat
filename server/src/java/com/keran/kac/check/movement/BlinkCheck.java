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
 * 瞬移检测(Blink/大位移)：单次移动事件位移过大。
 *
 * <p><b>v3.0 误报修复</b>：豁免末影珍珠、紫颂果传送、下界门/传送门、
 * 被爆炸推动、以及高 ping 玩家的延迟补偿；要求连续大位移才算违规。
 */
public class BlinkCheck extends Check {

    public BlinkCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.BLINK);
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
        // 刚受到伤害（爆炸/远程击退可造成大位移）
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
            decay(data);
            return;
        }
        // 鞘翅/游泳本身位移大
        if (p.isGliding() || p.isSwimming() || MoveUtil.isInLiquid(p)
                || MoveUtil.isInBubbleColumn(p)) {
            resetEvidence(data);
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();
        double max = plugin.getConfigManager().getCheckDouble(type, "max-distance", 3.0) * lagFactor;

        if (dist > max) {
            double over = (dist - max) / max;
            double severity = Math.min(3.0, 1.2 + over * 2.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "单次位移 %.2f 格 (上限 %.2f)", dist, max), severity);
            }
        } else {
            decay(data);
        }
    }
}
