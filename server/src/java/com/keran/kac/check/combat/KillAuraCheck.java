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

import java.util.Locale;

/**
 * 自瞄/杀戮光环检测(KillAura)。
 *
 * <p><b>v3.0 误报修复</b>（老版枪械服不可用）：
 * <ul>
 *   <li><b>多目标检测</b>：老版 {@code max-targets-per-tick: 4}，
 *       但<b>爆炸/范围伤害/榴弹/霰弹枪</b>一次命中多个实体是合法的。
 *       新版要求"同 tick 攻击多个<b>互不相邻</b>的目标"（爆炸会命中聚集的敌人，
 *       KillAura 会同时攻击彼此很远的目标）才计违规，并大幅提高容忍数。</li>
 *   <li><b>夹角检测</b>：老版用 60° 阈值，但<b>近战扫击、目标移动、时序差</b>都会产生大夹角。
 *       新版改用<b>视线→目标方向的持续偏离分析</b>，并要求连续异常。</li>
 *   <li>新增<b>目标切换速度</b>分析：真 KillAura 会在极短 tick 内切换目标并稳定命中。</li>
 *   <li>枪械弹射物伤害不参与。</li>
 * </ul>
 */
public class KillAuraCheck extends Check {

    public KillAuraCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.KILLAURA);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (attacker == victim) {
            return; // 自残/爆炸自伤不检测
        }

        long tick = plugin.getCheckManager().getTick();

        // ================= 检查一：同 tick 多目标（要求目标彼此分散） =================
        // 老版仅计数，爆炸/范围伤害会误报 → 新版要求目标间距离足够远，
        // 因为范围伤害命中的目标必然是聚集的，而 KillAura 会选择任意目标。
        int maxTargets = plugin.getConfigManager().getCheckInt(type, "max-targets-per-tick", 3);
        double spreadDistance = plugin.getConfigManager().getCheckDouble(type, "target-spread-distance", 3.0);

        if (maxTargets > 0) {
            if (tick == data.lastAttackTick) {
                data.attacksSameTick++;
                // 只有"分散"的多个目标才算 KillAura 特征
                double spread = data.lastAttackTargetLoc != null
                        ? data.lastAttackTargetLoc.distance(victim.getLocation()) : 0;
                data.lastAttackTargetLoc = victim.getLocation();

                if (data.attacksSameTick > maxTargets && spread >= spreadDistance) {
                    double severity = 2.0;
                    if (observe(data, severity)) {
                        flag(data, String.format(Locale.ROOT,
                                "同 tick 攻击 %d 个分散目标 (间距 %.1f 格)",
                                data.attacksSameTick, spread), severity);
                    }
                    data.attacksSameTick = 0;
                }
            } else {
                data.lastAttackTick = tick;
                data.attacksSameTick = 1;
                data.lastAttackTargetLoc = victim.getLocation();
            }
        }

        // ================= 检查二：目标切换速度 =================
        // 通过上一 tick 记录的目标判断是否频繁切换
        int switchThreshold = plugin.getConfigManager().getCheckInt(type, "fast-switch-count", 6);
        if (data.lastTargetSwitchTick > 0 && tick - data.lastTargetSwitchTick <= 2) {
            data.targetsSwitchedFast++;
            if (data.targetsSwitchedFast >= switchThreshold) {
                double severity = 1.3;
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "极短时间内切换目标 %d 次", data.targetsSwitchedFast), severity);
                }
                data.targetsSwitchedFast = 0;
            }
        } else {
            data.targetsSwitchedFast = Math.max(0, data.targetsSwitchedFast - 1);
        }
        data.lastTargetSwitchTick = tick;

        // ================= 检查三：视线偏离（持续偏离才是特征） =================
        double maxAngle = plugin.getConfigManager().getCheckDouble(type, "max-angle", 75);
        if (maxAngle <= 0) {
            decay(data);
            return;
        }
        Location eye = attacker.getEyeLocation();
        Location target = victim instanceof LivingEntity
                ? ((LivingEntity) victim).getEyeLocation()
                : victim.getLocation();

        Vector toTarget = target.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 0.01) {
            decay(data);
            return;
        }
        toTarget.normalize();
        Vector look = eye.getDirection();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(toTarget)));
        double angle = Math.toDegrees(Math.acos(dot));
        data.lastAttackAngle = angle;

        if (angle > maxAngle) {
            double over = (angle - maxAngle) / maxAngle;
            double severity = Math.min(3.0, 1.2 + over * 2.0);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "攻击夹角 %.1f° 超出视野 (目标 %s)", angle, victim.getName()), severity);
            }
        } else {
            decay(data);
        }
    }
}
