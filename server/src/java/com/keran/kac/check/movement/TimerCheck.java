package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 加速检测(Timer)：统计每秒移动包数量。
 *
 * <p><b>v3.0 误报修复</b>：
 * <ul>
 *   <li>老版把"位置包"和"视角包"混在一起数。玩家<b>抖屏/高刷新率客户端</b>会发送大量
 *       rotation-only 包，导致误报。新版<b>只统计位置真正变化的包</b>（position 包），
 *       视角包单独计数并排除。</li>
 *   <li>加入延迟补偿：高 ping 玩家包会更集中地到达，阈值放宽。</li>
 *   <li>要求连续多个统计窗口超标才计违规。</li>
 * </ul>
 */
public class TimerCheck extends Check {

    public TimerCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.TIMER);
    }

    @Override
    public void onMove(PlayerMoveEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (MoveUtil.canFly(p) || p.isDead()) {
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        boolean positionChanged = Math.abs(dx) > 0.0001 || Math.abs(dy) > 0.0001 || Math.abs(dz) > 0.0001;

        long now = System.currentTimeMillis();
        if (data.packetWindowStart == 0) {
            data.packetWindowStart = now;
        }
        if (positionChanged) {
            data.positionPackets++;
        } else {
            data.rotationPackets++;
        }

        if (now - data.packetWindowStart >= 1000) {
            int positions = data.positionPackets;
            data.positionPackets = 0;
            int rotations = data.rotationPackets;
            data.rotationPackets = 0;
            data.packetWindowStart = now;

            // 延迟补偿：高 ping 时包到达更集中
            double lagFactor = 1.0 + (Math.max(0, MoveUtil.pingOf(p)) / 100.0)
                    * plugin.getConfigManager().getLagCompensationRatio();
            int maxMoves = (int) Math.round(
                    plugin.getConfigManager().getCheckInt(type, "max-moves-per-second", 32) * lagFactor);

            if (positions > maxMoves) {
                double over = (double) (positions - maxMoves) / maxMoves;
                double severity = Math.min(3.0, 0.8 + over * 2.5);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "位置包 %d/秒 (上限 %d, 视角包 %d, 正常约20)",
                            positions, maxMoves, rotations), severity);
                }
            } else {
                decay(data);
            }
        }
    }
}
