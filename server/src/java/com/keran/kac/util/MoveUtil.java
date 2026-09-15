package com.keran.kac.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * 移动/方块相关的公共判定工具。
 */
public final class MoveUtil {

    private MoveUtil() {
    }

    /** 玩家是否拥有飞行能力(创造/飞行权限/幽灵) */
    public static boolean canFly(Player p) {
        return p.getAllowFlight()
                || p.getGameMode().name().equals("CREATIVE")
                || p.getGameMode().name().equals("SPECTATOR")
                || p.isFlying();
    }

    /** 玩家是否在可攀爬方块(梯子/藤蔓)中 */
    public static boolean isClimbing(Block block) {
        if (block == null) {
            return false;
        }
        Material m = block.getType();
        return m == Material.LADDER
                || m == Material.VINE
                || (m.name().contains("TWISTING_VINES"))
                || (m.name().contains("WEEPING_VINES"));
    }

    /** 方块是否为液体 */
    public static boolean isLiquid(Block block) {
        if (block == null) {
            return false;
        }
        return block.isLiquid();
    }

    /** 玩家身体是否浸在液体中 */
    public static boolean isInLiquid(Player p) {
        return isLiquid(p.getLocation().getBlock())
                || isLiquid(p.getLocation().clone().add(0, 0.5, 0).getBlock())
                || isLiquid(p.getLocation().clone().add(0, 1.0, 0).getBlock());
    }

    /** 脚下(0.1 格下方)是否为固体地面 */
    public static boolean isOnGround(Player p) {
        Block below = p.getLocation().clone().add(0, -0.1, 0).getBlock();
        return below.getType().isSolid() && !isClimbing(below) && !isLiquid(below);
    }

    /** 脚下是否为冰(滑行加速, 豁免速度检测) */
    public static boolean isOnIce(Player p) {
        Block below = p.getLocation().clone().add(0, -0.2, 0).getBlock();
        String n = below.getType().name();
        return n.contains("ICE") || n.contains("FROSTED");
    }

    /** 玩家是否在乘坐载具 */
    public static boolean isInVehicle(Player p) {
        return p.isInsideVehicle();
    }

    /** 玩家是否拥有缓降效果 */
    public static boolean hasSlowFalling(Player p) {
        return p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /** 玩家是否拥有速度药水效果及其等级(无则 -1) */
    public static int getSpeedPotionLevel(Player p) {
        return p.getPotionEffect(PotionEffectType.SPEED) != null
                ? p.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1 : -1;
    }

    /** 玩家脚边(水平相邻 0.3 处)是否有固体方块(贴墙) */
    public static boolean isBesideWall(Player p) {
        org.bukkit.Location loc = p.getLocation();
        for (double dx = -0.3; dx <= 0.3; dx += 0.6) {
            for (double dz = -0.3; dz <= 0.3; dz += 0.6) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block b = loc.clone().add(dx, 0.1, dz).getBlock();
                if (b.getType().isSolid() && !isClimbing(b) && !isLiquid(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 方块是否为 1.16.5 稀有矿物(透视检测统计用) */
    public static boolean isOre(Material m) {
        if (m == null) {
            return false;
        }
        String n = m.name();
        return n.contains("DIAMOND_ORE")
                || n.contains("EMERALD_ORE")
                || n.contains("GOLD_ORE")
                || n.contains("LAPIS_ORE")
                || n.contains("REDSTONE_ORE")
                || n.contains("ANCIENT_DEBRIS")
                || n.contains("QUARTZ_ORE")
                || n.contains("IRON_ORE")
                || n.contains("COAL_ORE")
                || n.contains("NETHER_GOLD_ORE")
                || n.equals("DEEPSLATE_DIAMOND_ORE")
                || n.equals("DEEPSLATE_EMERALD_ORE")
                || n.equals("DEEPSLATE_GOLD_ORE")
                || n.equals("DEEPSLATE_IRON_ORE")
                || n.equals("DEEPSLATE_LAPIS_ORE")
                || n.equals("DEEPSLATE_REDSTONE_ORE")
                || n.equals("DEEPSLATE_COAL_ORE");
    }

    /** 方块是否为"珍贵"矿物(透视检测高权重) */
    public static boolean isPreciousOre(Material m) {
        if (m == null) {
            return false;
        }
        String n = m.name();
        return n.contains("DIAMOND_ORE")
                || n.contains("EMERALD_ORE")
                || n.contains("ANCIENT_DEBRIS")
                || n.equals("DEEPSLATE_DIAMOND_ORE")
                || n.equals("DEEPSLATE_EMERALD_ORE");
    }
}
