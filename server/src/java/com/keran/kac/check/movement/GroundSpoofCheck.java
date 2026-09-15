package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 落地伪装检测(GroundSpoof)：玩家客户端上报"已落地"但服务端判定其明显处于空中下坠。
 *
 * <p>这是<b>新增检测</b>，专门抓 GroundSpoof 类作弊（通过伪造 onGround 绕过
 * 摔落伤害、实现 NoFall / Flight 的辅助手段）。
 * <ul>
 *   <li>要求：客户端 {@code isOnGround()==true} 但服务端多采样判定<b>完全无支撑</b>，
 *       且玩家正在<b>下落</b>（dy < -0.1）。</li>
 *   <li>与 Criticals 的区别：Criticals 检测"悬空却说落地来骗暴击"（玩家静止或上移），
 *       本检测专注"下坠中却说落地"（骗摔落伤害）。</li>
 *   <li>豁免：液体/蛛网/细雪/气泡柱/梯子/脚手架/缓降/鞘翅/载具。</li>
 * </ul>
 */
public class GroundSpoofCheck extends Check {

    public GroundSpoofCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.GROUNDSPOOF);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.groundSpoofTicks = 0;
            resetEvidence(data);
            return;
        }
        if (MoveUtil.isInLiquid(p) || MoveUtil.isInWeb(p) || MoveUtil.isInPowderSnow(p)
                || MoveUtil.isInBubbleColumn(p) || MoveUtil.hasSlowFalling(p)
                || MoveUtil.hasLevitation(p) || p.isGliding()
                || MoveUtil.isClimbing(e.getTo().getBlock())
                || MoveUtil.isOnScaffolding(p) || MoveUtil.isOnHoneyOrSlime(p)) {
            data.groundSpoofTicks = 0;
            resetEvidence(data);
            return;
        }
        if (isMovementExempt(data)) {
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();

        // 客户端说落地 + 服务端判定完全无支撑 + 正在下落
        boolean clientSaysGround = data.wasOnGround;
        boolean reallyUnsupported = !MoveUtil.hasNearbySolidSupport(p)
                && !MoveUtil.isOnBlockEdge(p);
        boolean descending = dy < -0.1;

        if (clientSaysGround && reallyUnsupported && descending) {
            data.groundSpoofTicks++;
            int required = plugin.getConfigManager().getCheckInt(type, "min-ticks", 4);
            if (data.groundSpoofTicks >= required) {
                double severity = Math.min(3.0, 1.5 + Math.abs(dy) * 2.0);
                if (observe(data, severity)) {
                    flag(data, String.format(java.util.Locale.ROOT,
                            "下坠中(%.3f/tick)上报落地但无支撑 (连续 %d ticks)",
                            dy, data.groundSpoofTicks), severity);
                }
                data.groundSpoofTicks = 0;
            }
        } else {
            data.groundSpoofTicks = 0;
            decay(data);
        }
    }
}
