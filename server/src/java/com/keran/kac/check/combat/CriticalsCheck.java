package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Locale;

/**
 * 异常暴击检测(Criticals)：识别"原地暴击"式作弊（伪造 onGround 来骗服务器判定暴击）。
 *
 * <p><b>v3.0 误报修复</b>（老版是误报率最高的战斗检测）：
 * <ul>
 *   <li>老版用自研的 {@code MoveUtil.isOnGround}（仅查脚下一格方块）去对比客户端 {@code isOnGround}，
 *       两者判定规则不同 —— <b>台阶边缘、半砖、栅栏、灵魂沙、床、垫高方块上攻击全部误报</b>。
 *       新版改为<b>多采样点 + 客户端容差</b>判定，并豁免所有"客户端可能判定为落地"的形状。</li>
 *   <li>老版依赖 {@code lastDy}（最近一次移动的垂直位移），玩家<b>原地不动时该值陈旧为 0</b>，
 *       导致站桩连击全部误报。新版要求<b>必须有正在进行的攻击且确实处于空中</b>。</li>
 *   <li>加入连续帧验证：真 Criticals 作弊会<b>每次攻击都异常</b>，而正常玩家只是偶发边界情况。</li>
 *   <li>豁免：跳跃攻击（真暴击）、下落攻击（真暴击）、被击退中、水里、梯子上、蛛网、气泡柱。</li>
 * </ul>
 */
public class CriticalsCheck extends Check {

    public CriticalsCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.CRITICALS);
    }

    @Override
    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        if (attacker == e.getEntity()) {
            return;
        }

        // ---------- 合法暴击场景豁免 ----------
        // 真正在空中的攻击（跳跃/下落）就是合法暴击，不检测
        if (!attacker.isOnGround()) {
            // 客户端报告在空中 —— 这与"伪造落地"无关，直接放行
            decay(data);
            return;
        }
        // 环境豁免
        if (MoveUtil.isInLiquid(attacker) || MoveUtil.isInWeb(attacker)
                || MoveUtil.isInBubbleColumn(attacker) || MoveUtil.isInPowderSnow(attacker)
                || MoveUtil.isClimbing(attacker.getLocation().getBlock())
                || MoveUtil.isOnScaffolding(attacker) || attacker.isGliding()
                || attacker.isInsideVehicle()) {
            decay(data);
            return;
        }
        // 被击退中豁免
        if (isMovementExempt(data)) {
            decay(data);
            return;
        }

        // ---------- 核心判定：客户端说"在地面"，但实际悬空 ----------
        // 只有当"客户端报告落地"与"服务端多采样判定确实悬空"同时成立时才可疑。
        //
        // 关键修复：使用严格的多点采样（含容差），只有【完全找不到任何支撑】才算悬空，
        // 避免台阶/半砖/栅栏边缘（客户端会判定落地，但单点采样会漏）误报。
        boolean reallySupported = MoveUtil.hasNearbySolidSupport(attacker);
        if (reallySupported) {
            // 脚下或身边确有支撑 —— 正常站桩攻击，放行
            decay(data);
            return;
        }
        // 也没有任何攀爬/液体等支撑 → 确属"悬空却报告落地"
        // 但还要求：玩家没有正在下落（正在下落说明是从高处落下，位置滞后是正常的）
        if (data.falling || data.lastDy < -0.06) {
            decay(data);
            return;
        }
        // 要求最近确实有移动数据（排除"原地不动导致 lastDy 陈旧"的老版误报）
        long sinceMove = plugin.getCheckManager().getTick() - data.lastMoveTick;
        if (sinceMove > 10) {
            // 很久没有移动包，数据不可信，不做判定
            decay(data);
            return;
        }

        // ---------- 证据累积：真作弊每次攻击都异常 ----------
        double severity = 1.3;
        if (observe(data, severity)) {
            flag(data, String.format(Locale.ROOT,
                    "悬空状态报告落地并攻击 (y=%.2f, dy=%.3f, 目标 %s)",
                    attacker.getLocation().getY(), data.lastDy, e.getEntity().getName()), severity);
        }
    }
}
