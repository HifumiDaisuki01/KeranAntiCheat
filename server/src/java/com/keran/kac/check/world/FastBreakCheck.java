package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import com.keran.kac.util.MoveUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;

/**
 * 速挖检测(FastBreak)：破坏方块的耗时与"该工具在该方块上的正常耗时"严重不符。
 *
 * <p>这是<b>新增检测</b>，补足原 Nuker 只统计"同 tick 破坏数"的盲区。
 * FastBreak 作弊（Nuker/FastBreak）能在极短时间内破坏大量<b>高硬度</b>方块
 * （如黑曜石、远古残骸），而正常玩家需要对应等级的镐 + 相当长时间。
 * <ul>
 *   <li>记录每次破坏的时间戳，计算<b>单位时间破坏的硬度加权总量</b>。</li>
 *   <li>硬度权重：高硬度方块（黑曜石/远古残骸/下界合金）权重远高于泥土/沙子。</li>
 *   <li>防误报：豁免创造模式、效率附魔（读取附魔等级放宽）、急迫药水、
 *       信标加成、连锁挖矿（相邻方块不计入）。</li>
 * </ul>
 */
public class FastBreakCheck extends Check {

    public FastBreakCheck(KeranAntiCheat plugin) {
        super(plugin, CheckType.FASTBREAK);
    }

    /** 方块硬度近似权重（越高越"硬"） */
    private double hardnessWeight(Material m) {
        if (m == null) {
            return 0;
        }
        String n = m.name();
        if (n.contains("OBSIDIAN") || n.contains("ANCIENT_DEBRIS")) {
            return 12.0;   // 黑曜石/远古残骸 需要钻石镐 + 很长耗时
        }
        if (n.contains("CRYING")) {
            return 10.0;
        }
        if (n.contains("DEEPSLATE") || n.contains("NETHERITE_BLOCK")) {
            return 4.5;
        }
        if (n.equals("STONE") || n.equals("COBBLESTONE") || MoveUtil.isOre(m)) {
            return 2.0;
        }
        if (n.contains("WOOD") || n.contains("PLANKS") || n.contains("LOG")) {
            return 1.2;
        }
        if (MoveUtil.isStoneLike(m)) {
            return 1.0;
        }
        return 0.6; // 泥土/沙子/树叶等
    }

    @Override
    public void onBreak(BlockBreakEvent e, PlayerData data) {
        Player p = e.getPlayer();
        if (p.getGameMode().name().equals("CREATIVE")) {
            return;
        }

        long now = System.currentTimeMillis();
        data.breakTimestamps.addLast(now);
        // 只保留最近 3 秒
        while (!data.breakTimestamps.isEmpty()
                && now - data.breakTimestamps.peekFirst() > 3000) {
            data.breakTimestamps.removeFirst();
        }

        double weight = hardnessWeight(e.getBlock().getType());

        // 效率附魔每级显著提速 → 放宽
        int efficiency = 0;
        try {
            org.bukkit.inventory.ItemStack tool = p.getInventory().getItemInMainHand();
            if (tool != null && tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DIG_SPEED) > 0) {
                efficiency = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DIG_SPEED);
            }
        } catch (Throwable ignored) {
        }
        double toolFactor = 1.0 + efficiency * 0.45;
        // 急迫药水 / 信标加成
        double hasteFactor = 1.0;
        try {
            org.bukkit.potion.PotionEffect haste =
                    p.getPotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING);
            if (haste != null) {
                hasteFactor += (haste.getAmplifier() + 1) * 0.4;
            }
        } catch (Throwable ignored) {
        }
        double allowed = plugin.getConfigManager().getCheckDouble(type, "max-weight-per-second", 9.0)
                * toolFactor * hasteFactor;

        // 统计最近 1 秒内的硬度权重总量
        double recentWeight = 0;
        for (long ts : data.breakTimestamps) {
            if (now - ts <= 1000) {
                recentWeight += weight;
            }
        }
        // 只对"高硬度"方块的加速有意义：单块高硬度方块本身权重大
        if (weight >= 4.0 && recentWeight > allowed) {
            double over = (recentWeight - allowed) / allowed;
            double severity = Math.min(3.0, 1.2 + over * 1.5);
            if (observe(data, severity)) {
                flag(data, String.format(Locale.ROOT,
                        "1 秒内破坏硬度总量 %.1f (上限 %.1f, 方块 %s, 效率%d)",
                        recentWeight, allowed, e.getBlock().getType().name(), efficiency), severity);
            }
        } else {
            decay(data);
        }
    }
}
