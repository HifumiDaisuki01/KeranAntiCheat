package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 飞行检测：持续上升 / 超出跳跃高度 / 空中悬浮。
 * 自动豁免：飞行权限、创造/观察者、载具、液体、攀爬、鞘翅、击退、传送、缓降(悬浮)。
 */
public class FlyCheck extends Check {

    public FlyCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.FLY);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            reset(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();
        double dy = to.getY() - from.getY();

        // 液体/攀爬/鞘翅中重置滞空状态
        if (MoveUtil.isInLiquid(p) || MoveUtil.isClimbing(to.getBlock())
                || MoveUtil.isClimbing(to.clone().add(0, -0.1, 0).getBlock())
                || p.isGliding()) {
            reset(data);
            return;
        }

        if (MoveUtil.isOnGround(p)) {
            reset(data);
            return;
        }

        data.airTicks++;
        int maxAirTicks = plugin.getConfigManager().getCheckInt(type, "max-air-ticks", 18);

        // --- 持续上升检测 ---
        if (dy > 0.01) {
            data.ascendTicks++;
            if (!data.inAirSince) {
                data.inAirSince = true;
                data.takeoffY = from.getY();
            }
        } else {
            data.ascendTicks = Math.max(0, data.ascendTicks - 2);
        }

        if (data.ascendTicks > maxAirTicks) {
            flag(data, "持续上升 " + data.ascendTicks + " ticks (dy=" + trim(dy) + ") 疑似飞行", 1);
            data.ascendTicks = 0;
        }

        // --- 跳跃高度检测(空中高于起跳点 1.35 格以上, 正常跳跃最高 1.25) ---
        if (data.inAirSince && data.airTicks > 12
                && (to.getY() - data.takeoffY) > 1.35) {
            flag(data, "悬停高度异常: 高于起跳点 " + trim(to.getY() - data.takeoffY) + " 格", 1);
            data.inAirSince = false;
        }

        // --- 悬浮检测(垂直位移≈0 持续过久) ---
        if (Math.abs(dy) < 0.001) {
            data.hoverTicks++;
            int maxHover = plugin.getConfigManager().getCheckInt(type, "max-hover-ticks", 100);
            if (data.hoverTicks > maxHover && !MoveUtil.hasSlowFalling(p)) {
                flag(data, "空中悬浮 " + data.hoverTicks + " ticks", 1);
                data.hoverTicks = 0;
            }
        } else {
            data.hoverTicks = 0;
        }
    }

    private void reset(PlayerData data) {
        data.airTicks = 0;
        data.ascendTicks = 0;
        data.hoverTicks = 0;
        data.inAirSince = false;
    }
}
