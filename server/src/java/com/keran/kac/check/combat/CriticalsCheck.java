package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 异常暴击检测(Criticals)：
 * 暴击要求客户端上报"空中"状态。若玩家客户端上报在空中(isOnGround=false)
 * 但真实站在地面(脚下方块为固体)且垂直位移≈0, 说明伪造了落地状态 —— 经典 Criticals 作弊。
 */
public class CriticalsCheck extends Check {

    public CriticalsCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.CRITICALS);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        Player attacker = (Player) e.getDamager();
        if (attacker == e.getEntity()) {
            return;
        }
        // 上报在空中 + 真实站在地面 + 垂直位移≈0(没在下落) → 伪造 onGround
        if (!attacker.isOnGround() && MoveUtil.isOnGround(attacker)
                && Math.abs(data.lastDy) < 0.01) {
            flag(data, "站立状态上报在空中(疑似暴击作弊)", 1);
        }
    }
}
