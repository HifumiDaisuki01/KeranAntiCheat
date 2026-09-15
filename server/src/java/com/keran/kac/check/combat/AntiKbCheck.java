package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * 击退抵抗检测(AntiKB)。
 *
 * <p>这是<b>新增检测</b>，与 Velocity 的区别：
 * <ul>
 *   <li>{@code Velocity} 走服务端 {@code PlayerVelocityEvent} 流程 —— 检测"服务端下发了击退但玩家没位移"。</li>
 *   <li>{@code AntiKB} 更关注<b>服务端是否根本没机会下发击退</b>：
 *       结合击退抗性属性与受击频率，识别"玩家从未被推动过"的持续性特征。</li>
 * </ul>
 * 关键设计（防误报）：<b>必须排除下界合金甲/击退抗性附魔</b>（它们本就合法免疫击退），
 * 并且要求跨多次受击持续出现。
 */
public class AntiKbCheck extends Check {

    public AntiKbCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.ANTIKB);
    }

    @Override
    public void onVelocity(PlayerVelocityEvent e, PlayerData data) {
        Player p = e.getPlayer();
        // 记录服务端确实下发过击退
        data.lastVelocityTick = plugin.getCheckManager().getTick();

        // 合法免疫：击退抗性足够高
        double resist = MoveUtil.getKnockbackResistance(p);
        double exemptResist = plugin.getConfigManager().getCheckDouble(type, "exempt-kb-resist", 0.4);
        if (resist >= exemptResist) {
            data.kbResistStreak = 0;
            decay(data);
            return;
        }
        // 环境豁免
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInWeb(p) || MoveUtil.isBesideWall(p)
                || MoveUtil.isInPowderSnow(p) || MoveUtil.canFly(p)
                || p.isInsideVehicle() || p.isBlocking()) {
            data.kbResistStreak = 0;
            decay(data);
            return;
        }

        // 服务端下发了击退向量，检查玩家是否实际执行
        org.bukkit.util.Vector v = e.getVelocity();
        double expected = Math.hypot(v.getX(), v.getZ());
        if (expected < 0.08) {
            // 击退本身就极小（低击退武器），不具备判定意义
            data.kbResistStreak = 0;
            decay(data);
            return;
        }
        // 记录期望击退幅度，供 onTick 校验
        data.lastHitOffset = expected;
    }

    @Override
    public void onTick() {
        long tick = plugin.getCheckManager().getTick();
        int checkDelay = plugin.getConfigManager().getCheckInt(type, "check-delay-ticks", 4);

        for (PlayerData data : plugin.getCheckManager().getAllData()) {
            if (data.lastVelocityTick != tick - checkDelay) {
                continue;
            }
            Player p = org.bukkit.Bukkit.getPlayer(data.getUuid());
            if (p == null || !p.isOnline() || p.isDead()) {
                continue;
            }
            org.bukkit.util.Vector v = p.getVelocity();
            double actual = Math.hypot(v.getX(), v.getZ());
            // 服务端下发了击退，但玩家速度几乎为 0，且位移也没有
            if (actual < 0.02 && data.lastHitOffset > 0.15) {
                data.kbResistStreak++;
                int required = plugin.getConfigManager().getCheckInt(type, "required-streak", 4);
                if (data.kbResistStreak >= required) {
                    double severity = 1.6;
                    if (observe(data, severity)) {
                        flag(data, String.format(java.util.Locale.ROOT,
                                "连续 %d 次收到击退但未产生位移 (期望 %.2f, 实际 %.3f)",
                                data.kbResistStreak, data.lastHitOffset, actual), severity);
                    }
                    data.kbResistStreak = 0;
                }
            } else {
                data.kbResistStreak = 0;
                decay(data);
            }
            data.lastHitOffset = 0;
        }
    }

    @Override
    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
        // 记录受击（供击退流程参考）
        if (e instanceof EntityDamageByEntityEvent) {
            data.lastDamageTick = plugin.getCheckManager().getTick();
        }
    }
}
