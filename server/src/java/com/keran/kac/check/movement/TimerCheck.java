package com.keran.kac.check.movement;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 加速检测(Timer)：统计每秒移动包数量。
 * 正常客户端约 20 包/秒, Timer 作弊可达 30+。
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
        long now = System.currentTimeMillis();
        if (data.movesThisSecond == 0) {
            data.timerWindowStart = now;
        }
        data.movesThisSecond++;

        if (now - data.timerWindowStart >= 1000) {
            int moves = data.movesThisSecond;
            data.movesThisSecond = 0;
            data.timerWindowStart = now;

            int maxMoves = plugin.getConfigManager().getCheckInt(type, "max-moves-per-second", 28);
            if (moves > maxMoves) {
                flag(data, "移动包 " + moves + "/秒 (正常约20)", 1);
            }
        }
    }
}
