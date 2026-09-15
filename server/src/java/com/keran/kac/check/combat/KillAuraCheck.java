package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

/**
 * 自瞄/杀戮光环检测(KillAura)：
 * 1) 同一 tick 攻击多个不同目标(多目标 KillAura)
 * 2) 攻击方向与视线夹角过大(视角外攻击, 典型的 KillAura/自瞄特征)
 * 仅对"近战"(damager 为玩家本体)生效, 枪械弹射物伤害不参与, 避免误报。
 */
public class KillAuraCheck extends Check {

    public KillAuraCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.KILLAURA);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        Player attacker = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (attacker == victim) {
            return; // 自残/爆炸自伤不检测
        }

        long tick = plugin.getCheckManager().getTick();

        // ---- 多目标检测 ----
        int maxTargets = plugin.getConfigManager().getCheckInt(type, "max-targets-per-tick", 4);
        if (maxTargets > 0) {
            if (tick == data.lastAttackTick) {
                data.attacksSameTick++;
            } else {
                data.lastAttackTick = tick;
                data.attacksSameTick = 1;
            }
            if (data.attacksSameTick > maxTargets) {
                flag(data, "同 tick 攻击 " + data.attacksSameTick + " 个不同目标", 1);
                data.attacksSameTick = 0;
            }
        }

        // ---- 攻击角度检测 ----
        double maxAngle = plugin.getConfigManager().getCheckDouble(type, "max-angle", 60);
        if (maxAngle <= 0) {
            return;
        }
        Location eye = attacker.getEyeLocation();
        Location target = victim instanceof LivingEntity
                ? ((LivingEntity) victim).getEyeLocation()
                : victim.getLocation();

        Vector toTarget = target.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 0.01) {
            return;
        }
        toTarget.normalize();
        Vector look = eye.getDirection();
        double dot = look.dot(toTarget);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angle = Math.toDegrees(Math.acos(dot));

        if (angle > maxAngle) {
            flag(data, "攻击夹角 " + trim(angle) + "° (目标在视野外 " + victim.getName() + ")", 1);
        }
    }
}
