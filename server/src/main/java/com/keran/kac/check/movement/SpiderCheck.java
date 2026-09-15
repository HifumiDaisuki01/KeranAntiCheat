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
 * 爬墙检测(Spider)：贴墙持续上升且净高度超过跳跃极限。
 *
 * <p><b>v3.0 误报修复</b>：补全豁免（脚手架、梯子、藤蔓、细雪、气泡柱、蜂蜜/粘液、
 * 跳跃提升药水、蜘蛛网、被击退），并加入延迟补偿与连续帧验证。
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
            resetEvidence(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }
        if (MoveUtil.isClimbing(e.getTo().getBlock())
                || MoveUtil.isClimbing(e.getTo().clone().add(0, -0.1, 0).getBlock())
                || MoveUtil.isInLiquid(p) || MoveUtil.isInBubbleColumn(p)
                || MoveUtil.isInWeb(p) || MoveUtil.isInPowderSnow(p)
                || MoveUtil.isOnScaffolding(p) || MoveUtil.isOnHoneyOrSlime(p)
                || MoveUtil.hasLevitation(p) || MoveUtil.hasJumpBoost(p)) {
            data.wallTicks = 0;
            resetEvidence(data);
            return;
        }
        long sinceDamage = plugin.getCheckManager().getTick() - data.lastDamageTick;
        if (sinceDamage <= plugin.getConfigManager().getDamageExemptTicks()) {
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
            double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                    * plugin.getConfigManager().getLagCompensationRatio();
            int maxTicks = (int) Math.round(
                    plugin.getConfigManager().getCheckInt(type, "max-wall-ticks", 14) * lagFactor);
            double netRise = e.getTo().getY() - data.takeoffY;
            double maxRise = plugin.getConfigManager().getCheckDouble(type, "max-wall-rise", 1.8);
            if (data.wallTicks > maxTicks && netRise > maxRise) {
                double severity = Math.min(2.5, 1.0 + (netRise - maxRise) * 0.5);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "贴墙上升 %d ticks, 净高 %.2f 格", data.wallTicks, netRise), severity);
                }
                data.wallTicks = 0;
            }
        } else {
            data.wallTicks = 0;
            decay(data);
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE;
    }
}
