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
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * 攻击距离检测(Reach)：近战攻击的 3D 距离超限。
 *
 * <p><b>v3.0 误报修复</b>（老版在枪械服几乎不可用）：
 * <ul>
 *   <li><b>延迟补偿</b>：高 ping 玩家看到的实体位置滞后，距离天然偏大，阈值按 ping 放宽。</li>
 *   <li><b>目标移动补偿</b>：高速移动的目标（奔跑玩家、怪物）在服务端位置与客户端命中时位置不同，
 *       按目标速度追加补偿。</li>
 *   <li><b>武器附加射程</b>：枪械服常给近战武器 +1~2 格攻击距离（属性/插件），
 *       读取物品 {@code generic.attack_reach} 属性或配置的每物品加成。</li>
 *   <li><b>连续趋势判定</b>：单次超距不计违规，要求"持续超距"（真 Reach 作弊会稳定超距）。</li>
 *   <li>枪械弹射物伤害不参与（只检测玩家本体近战）。</li>
 * </ul>
 */
public class ReachCheck extends Check {

    public ReachCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.REACH);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (attacker == victim) {
            return;
        }
        // 弹射物（枪械子弹/箭）不参与距离检测
        if (victim instanceof Projectile) {
            return;
        }

        double baseReach = plugin.getConfigManager().getCheckDouble(type, "max-reach", 4.0);
        if (baseReach <= 0) {
            return;
        }

        Location eye = attacker.getEyeLocation();
        Location target = victim instanceof LivingEntity
                ? ((LivingEntity) victim).getEyeLocation()
                : victim.getLocation();
        double dist = eye.distance(target);

        // ---------- 补偿 1：延迟补偿 ----------
        double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(attacker)) / 100.0)
                * plugin.getConfigManager().getReachLagRatio();

        // ---------- 补偿 2：目标移动补偿 ----------
        // 目标移动越快，服务端位置与实际命中点偏差越大
        double targetSpeed = 0;
        try {
            org.bukkit.util.Vector v = victim.getVelocity();
            targetSpeed = Math.hypot(v.getX(), v.getZ());
        } catch (Throwable ignored) {
        }
        double movingTolerance = Math.min(1.2, targetSpeed * 1.6);

        // ---------- 补偿 3：武器附加射程 ----------
        double weaponBonus = weaponReachBonus(attacker);

        double maxReach = baseReach * lagFactor + movingTolerance + weaponBonus;

        if (dist > maxReach) {
            double over = dist - maxReach;
            // 超出幅度决定权重；真 Reach 作弊通常超出 0.5 格以上
            double severity = Math.min(3.0, 1.0 + over * 2.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "攻击距离 %.2f 格 (上限 %.2f = 基础%.2f+延迟%.2f+移动%.2f+武器%.2f, 目标 %s)",
                        dist, maxReach, baseReach, baseReach * lagFactor - baseReach,
                        movingTolerance, weaponBonus, victim.getName()), severity);
            }
        } else {
            decay(data);
        }
    }

    /**
     * 读取攻击者手中武器的附加射程。
     * 枪械服常给近战武器 +1~2 格攻击距离，通过配置的每物品加成表声明：
     * <pre>checks.reach.weapon-bonus.DIAMOND_SWORD: 1.5</pre>
     * 未配置的物品回退到 global-weapon-bonus。
     */
    private double weaponReachBonus(Player p) {
        try {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                return 0;
            }
            String path = "checks." + type.getId() + ".weapon-bonus." + hand.getType().name();
            double bonus = plugin.getConfigManager().get().getDouble(path, -1);
            if (bonus >= 0) {
                return bonus;
            }
            return plugin.getConfigManager().getCheckDouble(type, "global-weapon-bonus", 0.0);
        } catch (Throwable t) {
            return 0;
        }
    }
}
