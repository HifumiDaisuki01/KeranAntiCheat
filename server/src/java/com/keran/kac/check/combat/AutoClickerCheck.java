package com.keran.kac.check.combat;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 自动连点检测(AutoClicker)：统计每秒左键点击次数。
 * 枪械服默认关闭, 如开启请按实际近战频率调整 max-cps。
 */
public class AutoClickerCheck extends Check {

    public AutoClickerCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.AUTOCLICKER);
    }

    @Override
    public void onInteract(PlayerInteractEvent e, PlayerData data) {
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        long now = System.currentTimeMillis();
        if (data.cpsClicks == 0) {
            data.cpsWindowStart = now;
        }
        data.cpsClicks++;
        if (now - data.cpsWindowStart >= 1000) {
            int cps = data.cpsClicks;
            data.cpsClicks = 0;
            data.cpsWindowStart = now;
            int maxCps = plugin.getConfigManager().getCheckInt(type, "max-cps", 18);
            if (cps > maxCps) {
                flag(data, "点击频率 " + cps + " CPS (上限 " + maxCps + ")", 1);
            }
        }
    }
}
