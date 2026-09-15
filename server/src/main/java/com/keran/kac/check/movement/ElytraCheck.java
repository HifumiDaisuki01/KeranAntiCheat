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
 * 鞘翅加速检测：鞘翅滑翔时水平速度超限。
 * 仅对正处于鞘翅滑翔状态的玩家生效。
 *
 * <p><b>v3.0 误报修复</b>：豁免俯冲（向下滑翔本就会加速）、烟花推进、
 * 以及高 ping 延迟补偿；要求连续超标才算违规。
 */
public class ElytraCheck extends Check {

    public ElytraCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.ELYTRA);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (!p.isGliding() || p.isDead()) {
            resetEvidence(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();
        // 俯冲时（明显下降）水平速度本就会因重力转化而升高，豁免
        double diveThreshold = plugin.getConfigManager().getCheckDouble(type, "dive-exempt-dy", -0.35);
        if (dy < diveThreshold) {
            decay(data);
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double horizontal = Math.hypot(dx, dz);

        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                * plugin.getConfigManager().getLagCompensationRatio();
        double max = plugin.getConfigManager().getCheckDouble(type, "max-elytra-speed", 3.2) * lagFactor;

        if (horizontal > max) {
            double over = (horizontal - max) / max;
            double severity = Math.min(2.5, 0.9 + over * 2.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "鞘翅水平速度 %.3f 格/tick (上限 %.3f)", horizontal, max), severity);
            }
        } else {
            decay(data);
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE;
    }
}
