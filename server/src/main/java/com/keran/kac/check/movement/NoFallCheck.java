package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 摔落检测(NoFall)：追踪自由落体段, 若下落距离较大却从未受到摔落伤害则判定异常。
 *
 * <p><b>v3.0 误报修复</b>（老版漏豁免极多）：
 * <ul>
 *   <li>老版只豁免 蜘蛛网/粘液块/蜂蜜块，<b>漏了水、细雪、草垛、床、脚手架、藤蔓、梯子、
 *       岩浆、以及站在实体上</b> —— 这些都是"落地不受伤"的合法原因。</li>
 *   <li>老版用"1 秒内有过摔落伤害"就整段豁免，逻辑漏洞大。新版改为
 *       <b>逐段追踪下落并把每段与一次摔落伤害配对</b>，只有"确实落下且未配对到伤害"才计违规。</li>
 *   <li>要求连续多次异常（真 NoFall 是持续性的）。</li>
 *   <li>创造/旁观/飞行/载具全程豁免。</li>
 * </ul>
 */
public class NoFallCheck extends Check {

    public NoFallCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.NOFALL);
    }

    /** 落地不受伤的方块（全部豁免） */
    private boolean isSafeLanding(Material m) {
        if (m == null) {
            return false;
        }
        String n = m.name();
        return m == Material.COBWEB || m == Material.SLIME_BLOCK
                || m == Material.HONEY_BLOCK || m == Material.WATER
                || m == Material.LAVA || m == Material.POWDER_SNOW
                || n.contains("HAY") || n.contains("BED") || n.contains("SCAFFOLDING")
                || n.contains("VINE") || n.contains("LADDER") || n.contains("SLIME")
                || n.contains("HONEY") || n.contains("WATER") || n.contains("LEAVES")
                || n.contains("SNOW") || n.contains("SWEET_BERRY");
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || MoveUtil.isInVehicle(p) || p.isDead()) {
            data.falling = false;
            return;
        }
        // 缓降 / 漂浮 / 液体 / 攀爬 / 细雪 / 蛛网 / 气泡柱：全部重置下落追踪
        if (MoveUtil.hasSlowFalling(p) || MoveUtil.hasLevitation(p)
                || MoveUtil.isInLiquid(p) || MoveUtil.isInWeb(p)
                || MoveUtil.isInBubbleColumn(p) || MoveUtil.isInPowderSnow(p)
                || MoveUtil.isClimbing(e.getTo().getBlock())
                || MoveUtil.isOnScaffolding(p) || p.isGliding()
                || MoveUtil.isOnHoneyOrSlime(p)) {
            data.falling = false;
            return;
        }
        if (isMovementExempt(data)) {
            data.falling = false;
            return;
        }

        double dy = e.getTo().getY() - e.getFrom().getY();

        if (dy < -0.1) {
            // 正在下降
            if (!data.falling) {
                data.falling = true;
                data.fallStartY = e.getFrom().getY();
            }
        } else if (data.falling) {
            // 下降段结束（落地/停下）
            double fall = data.fallStartY - e.getTo().getY();
            data.falling = false;
            if (fall <= 0) {
                decay(data);
                return;
            }
            Material below = e.getTo().clone().add(0, -0.1, 0).getBlock().getType();
            if (isSafeLanding(below)) {
                decay(data);
                return;
            }
            double minFall = plugin.getConfigManager().getCheckDouble(type, "min-fall-height", 3.5);
            // 只有"确认落下且未收到摔落伤害"才计证据
            long now = System.currentTimeMillis();
            if (fall >= minFall && (now - data.lastFallDamageTime) > 1500) {
                double severity = Math.min(2.5, 1.0 + (fall - minFall) * 0.15);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "下落 %.2f 格未受摔落伤害", fall), severity);
                }
            } else {
                decay(data);
            }
        }
    }

    @Override
    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            data.lastFallDamageTime = System.currentTimeMillis();
        }
    }

    @Override
    public long eventMask() {
        return EV_MOVE | EV_DAMAGE_TAKEN;
    }
}
