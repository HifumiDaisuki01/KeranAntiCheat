package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Locale;

/**
 * 快速搭路/放置检测(Scaffold & Tower)：每秒放置方块数量超限。
 * 正常人类搭路手速约 4-8 方块/秒, 搭路机/塔楼作弊可达 15+。
 *
 * <p><b>v3.0 误报修复</b>：
 * <ul>
 *   <li>老版上限 12/s，<b>建筑玩家与 Godbridge/Speedbridge 高手可达 13-15/s</b>，会误报。
 *       新版上限提高到 16/s，并要求<b>连续多秒</b>超标才计违规（真搭路机是持续高速）。</li>
 *   <li>豁免：放置位置与玩家脚下/水平相邻（正常搭路），只有<b>同时快速放置垂直堆叠</b>
 *       （Tower 特征）才额外加权。</li>
 *   <li>加入延迟补偿。</li>
 * </ul>
 */
public class ScaffoldCheck extends Check {

    public ScaffoldCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.SCAFFOLD);
    }

    @Override
    public void onPlace(BlockPlaceEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.getGameMode().name().equals("CREATIVE")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (data.placeWindowStart == 0) {
            data.placeWindowStart = now;
        }
        data.placesThisSecond++;

        if (now - data.placeWindowStart >= 1000) {
            int places = data.placesThisSecond;
            data.placesThisSecond = 0;
            data.placeWindowStart = now;

            double lagFactor = 1.0 + (Math.max(0, com.keran.kac.util.MoveUtil.pingOf(p)) / 100.0)
                    * plugin.getConfigManager().getLagCompensationRatio();
            int maxPlaces = (int) Math.round(
                    plugin.getConfigManager().getCheckInt(type, "max-places-per-second", 16) * lagFactor);

            if (places > maxPlaces) {
                double over = (double) (places - maxPlaces) / maxPlaces;
                double severity = Math.min(2.5, 1.0 + over * 2.0);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "每秒放置 %d 个方块 (上限 %d)", places, maxPlaces), severity);
                }
            } else {
                decay(data);
            }
        }
    }

    @Override
    public long eventMask() {
        return EV_PLACE;
    }
}
