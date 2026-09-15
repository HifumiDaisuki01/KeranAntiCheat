package com.keran.kac.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * 移动/方块相关的公共判定工具（v3.0 大幅扩充豁免矩阵）。
 */
public final class MoveUtil {

    private MoveUtil() {
    }

    // ==================== 基础状态 ====================

    /** 玩家是否拥有飞行能力(创造/飞行权限/幽灵) */
    public static boolean canFly(Player p) {
        return p.getAllowFlight()
                || p.getGameMode().name().equals("CREATIVE")
                || p.getGameMode().name().equals("SPECTATOR")
                || p.isFlying();
    }

    /** 玩家是否在乘坐载具 */
    public static boolean isInVehicle(Player p) {
        return p.isInsideVehicle();
    }

    /** 玩家延迟(ms)，取不到时回退 0 */
    public static int pingOf(Player p) {
        try {
            return p.getPing();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ==================== 方块判定 ====================

    /** 玩家是否在可攀爬方块(梯子/藤蔓/洞穴藤蔓/缠怨藤/垂泪藤)中 */
    public static boolean isClimbing(Block block) {
        if (block == null) {
            return false;
        }
        Material m = block.getType();
        String n = m.name();
        return m == Material.LADDER
                || m == Material.VINE
                || n.contains("TWISTING_VINES")
                || n.contains("WEEPING_VINES")
                || n.contains("CAVE_VINES");
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
        Location l = p.getLocation();
        return isLiquid(l.getBlock())
                || isLiquid(l.clone().add(0, 0.5, 0).getBlock())
                || isLiquid(l.clone().add(0, 1.0, 0).getBlock());
    }

    /** 玩家是否身处气泡柱（向上/向下气泡都会推动玩家，必须豁免） */
    public static boolean isInBubbleColumn(Player p) {
        Location l = p.getLocation();
        for (double dy = 0; dy <= 1.8; dy += 0.6) {
            String n = l.clone().add(0, dy, 0).getBlock().getType().name();
            if (n.contains("BUBBLE_COLUMN")) {
                return true;
            }
        }
        return false;
    }

    /** 玩家是否身处蜘蛛网中（蜘蛛网内可缓慢悬浮/下降） */
    public static boolean isInWeb(Player p) {
        Location l = p.getLocation();
        return l.getBlock().getType() == Material.COBWEB
                || l.clone().add(0, 1, 0).getBlock().getType() == Material.COBWEB;
    }

    /** 是否站在脚手架/附近（脚手架可攀爬且可停留） */
    public static boolean isOnScaffolding(Player p) {
        Location l = p.getLocation();
        for (double dy = -0.5; dy <= 1.5; dy += 0.5) {
            if (l.clone().add(0, dy, 0).getBlock().getType() == Material.SCAFFOLDING) {
                return true;
            }
        }
        return false;
    }

    /** 玩家是否在细雪中（细雪会让人缓慢下沉/悬浮，且免疫摔落） */
    public static boolean isInPowderSnow(Player p) {
        Location l = p.getLocation();
        return l.getBlock().getType() == Material.POWDER_SNOW
                || l.clone().add(0, 1, 0).getBlock().getType() == Material.POWDER_SNOW;
    }

    /** 玩家是否踩在蜂蜜块/粘液块上（弹跳与粘滞会导致合法异常位移） */
    public static boolean isOnHoneyOrSlime(Player p) {
        String n = p.getLocation().clone().add(0, -0.3, 0).getBlock().getType().name();
        return n.contains("HONEY") || n.contains("SLIME");
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

    /**
     * 玩家是否站在方块边缘（用于飞行悬浮豁免）。
     * 站在台阶/栅栏/半砖边缘时，客户端与服务端的"是否落地"判定常不一致。
     */
    public static boolean isOnBlockEdge(Player p) {
        Location l = p.getLocation();
        int solidNeighbors = 0;
        for (double dx = -0.35; dx <= 0.35; dx += 0.7) {
            for (double dz = -0.35; dz <= 0.35; dz += 0.7) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (l.clone().add(dx, -0.2, dz).getBlock().getType().isSolid()) {
                    solidNeighbors++;
                }
            }
        }
        // 只有部分方向有支撑 = 站在边缘
        return solidNeighbors > 0 && solidNeighbors < 8;
    }

    /**
     * 玩家附近(水平 0.6 格内)是否有固体方块支撑。
     * 用于区分"真正悬空"与"卡在方块/栅栏/墙边"。
     */
    public static boolean hasNearbySolidSupport(Player p) {
        Location l = p.getLocation();
        for (double dx = -0.6; dx <= 0.6; dx += 0.3) {
            for (double dz = -0.6; dz <= 0.6; dz += 0.3) {
                for (double dy = -0.6; dy <= 0.2; dy += 0.4) {
                    Block b = l.clone().add(dx, dy, dz).getBlock();
                    if (b.getType().isSolid() && !isClimbing(b) && !isLiquid(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 玩家脚边(水平相邻 0.3 处)是否有固体方块(贴墙) */
    public static boolean isBesideWall(Player p) {
        Location loc = p.getLocation();
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

    // ==================== 药水效果 ====================

    /** 玩家是否拥有缓降效果 */
    public static boolean hasSlowFalling(Player p) {
        return p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /** 玩家是否拥有漂浮(潜影贝)效果 —— 会合法地让人上升 */
    public static boolean hasLevitation(Player p) {
        return p.hasPotionEffect(PotionEffectType.LEVITATION);
    }

    /** 玩家是否拥有跳跃提升效果 —— 会合法地提高跳跃高度 */
    public static boolean hasJumpBoost(Player p) {
        return p.hasPotionEffect(PotionEffectType.JUMP);
    }

    /** 玩家是否拥有速度药水效果及其等级(无则 -1) */
    public static int getSpeedPotionLevel(Player p) {
        return p.getPotionEffect(PotionEffectType.SPEED) != null
                ? p.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1 : -1;
    }

    /** 跳跃提升等级(无则 0) */
    public static int getJumpBoostLevel(Player p) {
        return p.getPotionEffect(PotionEffectType.JUMP) != null
                ? p.getPotionEffect(PotionEffectType.JUMP).getAmplifier() + 1 : 0;
    }

    /** 玩家是否拥有击退抗性相关附魔/效果（下界合金甲等） */
    public static double getKnockbackResistance(Player p) {
        try {
            org.bukkit.attribute.AttributeInstance attr =
                    p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            return attr == null ? 0.0 : attr.getValue();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** 玩家是否处于减速类负面效果中（会降低速度，需在速度检测里放宽） */
    public static boolean hasSlowness(Player p) {
        return p.hasPotionEffect(PotionEffectType.SLOW)
                || p.hasPotionEffect(PotionEffectType.SLOW_DIGGING);
    }

    // ==================== 矿物判定 ====================

    /** 方块是否为 1.20.1 稀有矿物(透视检测统计用) */
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
                || n.contains("COPPER_ORE")
                || n.contains("COAL_ORE")
                || n.contains("NETHER_GOLD_ORE");
    }

    /** 方块是否为"珍贵"矿物(透视检测高权重) */
    public static boolean isPreciousOre(Material m) {
        if (m == null) {
            return false;
        }
        String n = m.name();
        return n.contains("DIAMOND_ORE")
                || n.contains("EMERALD_ORE")
                || n.contains("ANCIENT_DEBRIS");
    }

    /** 方块是否为石头类（用于 XRay 路径分析：正常挖矿会大量出土石） */
    public static boolean isStoneLike(Material m) {
        if (m == null) {
            return false;
        }
        String n = m.name();
        return n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("COBBLESTONE")
                || n.equals("COBBLED_DEEPSLATE")
                || n.contains("TUFF") || n.equals("NETHERRACK") || n.equals("END_STONE")
                || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")
                || n.equals("DIRT") || n.equals("GRAVEL") || n.equals("SAND");
    }
}
