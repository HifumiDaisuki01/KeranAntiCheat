package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;

/**
 * 防击退检测(Velocity)：被实体攻击后短时间内水平位移过小。
 *
 * <p><b>v3.0 误报修复</b>：
 * <ul>
 *   <li>老版只看 {@code dist < 0.05}，但<b>在液体中受击、贴墙受击、站在半砖上、
 *       被极低击退武器命中</b>时位移本就接近 0，全部误报。</li>
 *   <li>新增豁免：<b>击退抗性附魔/属性</b>（下界合金甲可减免 100% 击退）、
 *       水中/岩浆、贴墙、蛛网、气泡柱、盾牌格挡、创造/旁观。</li>
 *   <li>新增豁免：<b>攻击者与受害者延迟</b>导致的时序错位（受击后位移尚未同步）。</li>
 *   <li>要求<b>连续多次</b>无位移才判定（真 AntiKB 是持续性的，偶发一次是网络/地形因素）。</li>
 * </ul>
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
        // 击退抗性高的玩家（下界合金甲/附魔）本来就不该有明显击退
        double kbResist = MoveUtil.getKnockbackResistance(victim);
        if (kbResist >= plugin.getConfigManager().getCheckDouble(type, "max-kb-resist", 0.5)) {
            return;
        }
        // 环境豁免：这些场景位移本就受限
        if (MoveUtil.isInLiquid(victim) || MoveUtil.isInWeb(victim)
                || MoveUtil.isInBubbleColumn(victim) || MoveUtil.isInPowderSnow(victim)
                || MoveUtil.isBesideWall(victim) || victim.isInsideVehicle()
                || MoveUtil.canFly(victim)) {
            return;
        }

        data.lastDamageTick = plugin.getCheckManager().getTick();
        data.damageLocation = victim.getLocation();
        // 记录受击时的击退抗性，供检查阶段参考
        data.lastHitOffset = kbResist;
    }

    @Override
    public void onTick() {
        long tick = plugin.getCheckManager().getTick();
        double minDist = plugin.getConfigManager().getCheckDouble(type, "min-velocity-distance", 0.08);
        int checkDelay = plugin.getConfigManager().getCheckInt(type, "check-delay-ticks", 3);

        for (PlayerData data : plugin.getCheckManager().getAllData()) {
            if (data.lastDamageTick != tick - checkDelay || data.damageLocation == null) {
                continue;
            }
            Player victim = Bukkit.getPlayer(data.getUuid());
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                data.damageLocation = null;
                continue;
            }
            Location now = victim.getLocation();
            double dx = now.getX() - data.damageLocation.getX();
            double dz = now.getZ() - data.damageLocation.getZ();
            double dist = Math.hypot(dx, dz);
            data.damageLocation = null;

            // 延迟补偿：高 ping 玩家位移同步滞后，放宽要求
            int ping = MoveUtil.pingOf(victim);
            double tolerant = minDist * (1.0 + (ping / 100.0) * 0.5);

            if (dist < tolerant) {
                // 记录连续无击退次数：真 AntiKB 是持续性的
                data.kbResistStreak++;
                int required = plugin.getConfigManager().getCheckInt(type, "required-streak", 3);
                if (data.kbResistStreak >= required) {
                    double severity = 1.5;
                    if (observe(data, severity)) {
                        flag(data, String.format(Locale.ROOT,
                                "受击后水平位移仅 %.3f 格 (连续 %d 次无击退)",
                                dist, data.kbResistStreak), severity);
                    }
                    data.kbResistStreak = 0;
                }
            } else {
                data.kbResistStreak = 0;
                decay(data);
            }
        }
    }
}
