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
 * 命中箱膨胀检测(Hitbox / HitboxExpander)。
 *
 * <p>这是<b>新增检测</b>。Hitbox 作弊把目标碰撞箱放大，使得"看起来没瞄准"也能命中。
 * 判定思路：计算<b>攻击射线与实际命中点之间的垂直偏移</b>。
 * <ul>
 *   <li>正常命中：攻击者视线射线穿过目标碰撞箱（偏移在目标体积半径内）。</li>
 *   <li>Hitbox 作弊：命中点明显偏离视线射线（射线根本没穿过目标，但仍造成伤害）。</li>
 * </ul>
 * 防误报：
 * <ul>
 *   <li>用目标实际碰撞箱尺寸（{@code getWidth()/getHeight()}）作为容差，而非固定值。</li>
 *   <li>加入延迟补偿（目标位置滞后会让偏移天然偏大）。</li>
 *   <li>要求连续多次异常。</li>
 * </ul>
 */
public class HitboxCheck extends Check {

    public HitboxCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.HITBOX);
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
        LivingEntity target = (LivingEntity) victim;
        if (MoveUtil.canFly(attacker)) {
            return;
        }

        // 计算从眼睛到目标中心的射线，与"眼睛到目标最近点"的偏差
        Location eye = attacker.getEyeLocation();
        Location center = target.getLocation().add(0, target.getHeight() / 2.0, 0);
        Vector toTarget = center.toVector().subtract(eye.toVector());
        double dist = toTarget.length();
        if (dist < 0.01) {
            return;
        }
        Vector dir = toTarget.clone().normalize();
        Vector look = eye.getDirection();

        // 视线与目标方向夹角 → 横向偏移（垂足距离）
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(dir)));
        double angle = Math.toDegrees(Math.acos(dot));
        // 横向偏移 ≈ 距离 × sin(夹角)
        double lateralOffset = dist * Math.sin(Math.toRadians(angle));

        // 容差 = 目标碰撞箱半径 + 延迟补偿
        double radius = target.getWidth() / 2.0;
        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(attacker)) / 100.0) * 0.20;
        double tolerance = (radius + 0.25) * lagFactor;

        data.lastHitOffset = lateralOffset;

        if (lateralOffset > tolerance) {
            double over = (lateralOffset - tolerance) / Math.max(0.1, tolerance);
            double severity = Math.min(3.0, 1.2 + over * 1.5);
            if (observe(data, severity)) {
                flag(data, String.format(java.util.Locale.ROOT,
                        "命中偏移 %.2f 格超出碰撞箱半径 %.2f (目标 %s, 夹角 %.1f°)",
                        lateralOffset, radius, target.getName(), angle), severity);
            }
        } else {
            decay(data);
        }
    }
}
