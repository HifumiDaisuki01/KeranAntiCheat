package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 视角旋转检测(Aim)：单 tick / 双 tick 内视角旋转速度异常。
 * 瞄准机器人可在 1 tick 内转向任意角度, 人类受鼠标灵敏度限制。
 */
public class AimCheck extends Check {

    public AimCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AIM);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.isDead()) {
            return;
        }

        float yaw = p.getLocation().getYaw();
        float pitch = p.getLocation().getPitch();
        long now = System.currentTimeMillis();
        long interval = now - data.lastRotationTime;

        double yawDelta = Math.abs(yaw - data.lastYaw);
        if (yawDelta > 180) {
            yawDelta = 360 - yawDelta;
        }
        double pitchDelta = Math.abs(pitch - data.lastPitch);

        // 间隔过长视为新的动作基线, 避免跨动作累计
        if (interval > 300) {
            data.lastYaw = yaw;
            data.lastPitch = pitch;
            data.lastRotationTime = now;
            return;
        }

        double maxPerTick = plugin.getConfigManager().getCheckDouble(type, "max-rotation-per-tick", 90);
        double maxTwoTicks = plugin.getConfigManager().getCheckDouble(type, "max-rotation-two-ticks", 135);

        double rotation = Math.max(yawDelta, pitchDelta);
        if (rotation > maxPerTick && interval <= 100) {
            flag(data, "单 tick 旋转 " + trim(rotation) + "° (yaw:" + trim(yawDelta) + " pitch:" + trim(pitchDelta) + ")", 1);
            data.lastYaw = yaw;
            data.lastPitch = pitch;
            data.lastRotationTime = now;
            return;
        }
        if (rotation > maxTwoTicks && interval <= 200) {
            flag(data, "快速转身 " + trim(rotation) + "°", 1);
        }

        data.lastYaw = yaw;
        data.lastPitch = pitch;
        data.lastRotationTime = now;
    }
}
