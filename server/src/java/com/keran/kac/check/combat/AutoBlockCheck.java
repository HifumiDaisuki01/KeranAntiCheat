package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 自动格挡检测(AutoBlock)：识别"攻击与格挡同时进行"的作弊行为。
 *
 * <p>这是<b>新增检测</b>。AutoBlock 作弊在攻击的瞬间自动举盾/格挡，
 * 从而抵消反击伤害 —— 人类无法在同一 tick 内既攻击又格挡。
 * <ul>
 *   <li>核心判据：造成伤害时，玩家仍然处于格挡状态（{@code isBlocking()}），
 *       且不是"举着盾靠近攻击"的正常行为。</li>
 *   <li>防误报：豁免盾牌右键持续格挡的正常场景 —— 要求<b>攻击后极短时间内
 *       立刻恢复格挡</b>（说明是自动切换），并要求连续多次。</li>
 *   <li>豁免：创造/旁观、被击退中。</li>
 * </ul>
 */
public class AutoBlockCheck extends Check {

    public AutoBlockCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AUTOBLOCK);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        if (attacker == e.getEntity() || MoveUtil.canFly(attacker)) {
            return;
        }
        // 攻击瞬间仍在格挡 —— 人类几乎不可能（攻击会中断格挡）
        boolean blocking = attacker.isBlocking();
        if (!blocking) {
            decay(data);
            return;
        }
        long tick = plugin.getCheckManager().getTick();
        // 要求"刚刚还在攻击"或"攻击后立刻格挡"，排除正常举盾接近
        long sinceAttack = tick - data.lastAttackTick;
        boolean suspicious = sinceAttack <= 2;

        if (suspicious) {
            data.blockingAttackCount++;
            int required = plugin.getConfigManager().getCheckInt(type, "required-count", 3);
            if (data.blockingAttackCount >= required) {
                double severity = 1.8;
                if (observe(data, severity)) {
                    flag(data, String.format(java.util.Locale.ROOT,
                            "连续 %d 次攻击同时保持格挡 (AutoBlock)", data.blockingAttackCount), severity);
                }
                data.blockingAttackCount = 0;
            }
        } else {
            data.blockingAttackCount = Math.max(0, data.blockingAttackCount - 1);
            decay(data);
        }
    }
}
