package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 防击退检测(Velocity)：被实体攻击(近战/弹射物)后 3 tick 内水平位移过小。
 * 自动豁免：盾牌格挡、环境伤害、非实体伤害。
 */
public class VelocityCheck extends Check {

    public VelocityCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.VELOCITY);
    }

    @Override
    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
        if (!(e instanceof EntityDamageByEntityEvent)) {
            return; // 环境伤害不检测
        }
        Player victim = (Player) e.getEntity();
        if (victim.isBlocking()) {
            return; // 盾牌格挡减免击退
        }
        if (e.isCancelled()) {
            return;
        }
        data.lastDamageTick = plugin.getCheckManager().getTick();
        data.damageLocation = victim.getLocation();
    }

    @Override
    public void onTick() {
        long tick = plugin.getCheckManager().getTick();
        double minDist = plugin.getConfigManager().getCheckDouble(type, "min-velocity-distance", 0.05);
        for (PlayerData data : plugin.getCheckManager().getAllData()) {
            if (data.lastDamageTick != tick - 3 || data.damageLocation == null) {
                continue;
            }
            Player victim = Bukkit.getPlayer(data.getUuid());
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                continue;
            }
            Location now = victim.getLocation();
            double dx = now.getX() - data.damageLocation.getX();
            double dz = now.getZ() - data.damageLocation.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist < minDist) {
                flag(data, "受击后水平位移仅 " + trim(dist) + " 格 (疑似防击退)", 1);
            }
            data.damageLocation = null;
        }
    }
}
