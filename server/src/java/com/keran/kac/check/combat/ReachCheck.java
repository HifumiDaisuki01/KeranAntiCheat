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

/**
 * 攻击距离检测(Reach)：近战攻击的 3D 距离超限。
 * 正常近战约 3.0-3.7 格(眼睛到目标中心), 阈值可配置。
 * 枪械弹射物伤害不参与; 若枪械插件自带近战武器, 可调高阈值或关闭。
 */
public class ReachCheck extends Check {

    public ReachCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.REACH);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        Player attacker = (Player) e.getDamager();
        Entity victim = e.getEntity();
        if (attacker == victim) {
            return;
        }

        double maxReach = plugin.getConfigManager().getCheckDouble(type, "max-reach", 4.0);
        if (maxReach <= 0) {
            return;
        }

        Location eye = attacker.getEyeLocation();
        Location target = victim instanceof LivingEntity
                ? ((LivingEntity) victim).getEyeLocation()
                : victim.getLocation();
        double dist = eye.distance(target);

        if (dist > maxReach) {
            flag(data, "攻击距离 " + trim(dist) + " 格 (上限 " + trim(maxReach) + ", 目标 " + victim.getName() + ")", 1);
        }
    }
}
