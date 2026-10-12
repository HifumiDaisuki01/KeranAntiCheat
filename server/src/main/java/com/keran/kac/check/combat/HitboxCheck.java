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

        Location eye = attacker.getEyeLocation();
        double dist = com.keran.kac.util.CombatGeometry.distanceToBox(eye.toVector(), target.getBoundingBox());
        double padding = 0.15 + Math.min(300, Math.max(0, MoveUtil.pingOf(attacker))) * 0.001;
        boolean intersects = target.getBoundingBox().clone().expand(padding)
                .rayTrace(eye.toVector(), eye.getDirection(), dist + target.getWidth() + target.getHeight() + 1) != null;
        if (!intersects) {
            double severity = 1.4;
            if (observe(data, severity)) {
                flag(data, String.format(java.util.Locale.ROOT,
                        "近战视线未穿过目标碰撞箱 (距离 %.2f, 容差 %.2f, 目标 %s)",
                        dist, padding, target.getName()), severity);
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
