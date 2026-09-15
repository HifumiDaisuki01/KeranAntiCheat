package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * 回溯攻击检测(Backtrack / LagSwitch)。
 *
 * <p>这是<b>新增检测</b>。Backtrack 作弊通过延迟发送移动包，
 * 让服务端认为目标仍在<b>过去的位置</b>，从而在目标已经离开后仍能命中。
 * <ul>
 *   <li>判据：攻击命中时，目标当前服务端位置与"攻击者客户端所见的延迟位置"
 *       之间存在<b>方向一致且幅度异常</b>的偏差。</li>
 *   <li>实现：结合攻击距离与延迟推算"允许的回溯窗口"，
 *       超过该窗口的距离偏差即视为回溯攻击。</li>
 *   <li>防误报：严格按 {@code ping/50} 换算允许的回溯 tick 数，
 *       并按目标移动速度折算允许距离；要求连续出现。</li>
 * </ul>
 */
public class BacktrackCheck extends Check {

    public BacktrackCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.BACKTRACK);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (attacker == victim || !(victim instanceof LivingEntity)) {
            return;
        }
        if (MoveUtil.canFly(attacker) || attacker.isInsideVehicle()) {
            return;
        }

        int ping = MoveUtil.pingOf(attacker);
        int maxPing = plugin.getConfigManager().getCheckInt(type, "max-compensated-ping", 300);
        if (ping > maxPing) {
            // 极高延迟玩家无法可靠判定，直接豁免（避免误报）
            return;
        }

        // 允许的回溯距离 = 目标速度 × 允许回溯 tick 数
        double targetSpeed;
        try {
            Vector tv = victim.getVelocity();
            targetSpeed = Math.hypot(tv.getX(), tv.getZ());
        } catch (Throwable t) {
            targetSpeed = 0;
        }
        // 允许回溯 tick 数按 ping 折算（ping/50 = 往返 tick 数的一半）
        double allowedTicks = (ping / 50.0) + plugin.getConfigManager().getCheckDouble(type, "extra-ticks", 1.0);
        double allowedDistance = targetSpeed * allowedTicks;
        double maxOffset = plugin.getConfigManager().getCheckDouble(type, "max-offset", 1.8);

        // 计算攻击者到目标的实际距离是否远超"考虑回溯后的合理距离"
        Location eye = attacker.getEyeLocation();
        Location targetCenter = victim.getLocation().add(0, victim.getHeight() / 2.0, 0);
        double dist = eye.distance(targetCenter);

        double baseReach = plugin.getConfigManager().getCheckDouble(type, "base-reach", 4.0);
        double limit = baseReach + Math.min(maxOffset, allowedDistance);

        if (dist > limit && targetSpeed > 0.05) {
            double over = dist - limit;
            double severity = Math.min(3.0, 1.0 + over * 1.5);
            if (observe(data, severity)) {
                flag(data, String.format(java.util.Locale.ROOT,
                        "疑似回溯命中: 距离 %.2f 超出允许 %.2f (ping %d, 目标速度 %.2f)",
                        dist, limit, ping, targetSpeed), severity);
            }
        } else {
            decay(data);
        }
    }

    @Override
    public long eventMask() {
        return EV_DAMAGE_DEALT;
    }
}
