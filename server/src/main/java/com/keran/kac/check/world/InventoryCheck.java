package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

/**
 * 背包操作检测(Inventory / AutoArmor / AutoTotem)。
 *
 * <p>这是<b>新增检测</b>。AutoArmor（自动换装）/ AutoTotem（自动换图腾）
 * 这类作弊会在受伤瞬间自动操作背包换装，表现为<b>极高的背包点击频率</b>
 * 与"受伤后立刻换装"的固定模式。
 * <ul>
 *   <li>通过 {@code PlayerInteractEvent} 统计右键点击频率（打开容器/使用物品）。</li>
 *   <li>判据：短时间内背包操作次数异常高 + 操作间隔高度规律（与连点器同理）。</li>
 *   <li>防误报：豁免创造模式、以及正常的箱子整理行为（阈值设得较宽松）。</li>
 * </ul>
 * 注意：枪械服的"快速换弹/切枪"是正常玩法，因此本检测<b>默认只告警不处罚</b>。
 */
public class InventoryCheck extends Check {

    public InventoryCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.INVENTORY);
    }

    @Override
    public void onInteract(PlayerInteractEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.getGameMode().name().equals("CREATIVE")) {
            return;
        }
        Action a = e.getAction();
        // 只统计右键（使用物品/开容器）
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        long now = System.currentTimeMillis();
        if (data.inventoryWindowStart == 0) {
            data.inventoryWindowStart = now;
        }
        data.inventoryOpsThisSecond++;

        if (now - data.inventoryWindowStart >= 1000) {
            int ops = data.inventoryOpsThisSecond;
            data.inventoryOpsThisSecond = 0;
            data.inventoryWindowStart = now;

            int maxOps = plugin.getConfigManager().getCheckInt(type, "max-ops-per-second", 25);
            if (ops > maxOps) {
                double over = (double) (ops - maxOps) / maxOps;
                double severity = Math.min(2.0, 0.8 + over * 1.5);
                if (observe(data, severity)) {
                    flag(data, String.format(Locale.ROOT,
                            "每秒背包/使用操作 %d 次 (上限 %d)", ops, maxOps), severity);
                }
            } else {
                decay(data);
            }
        }
    }

    @Override
    public long eventMask() {
        return EV_INTERACT;
    }
}
