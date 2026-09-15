package com.keran.kac.check;

/**
 * 检测器枚举定义。
 * 每个枚举项对应一种反作弊检测。
 */
public enum CheckType {

    // 移动检测
    FLY("fly", "飞行", "飞行/悬浮"),
    SPEED("speed", "速度", "移动加速"),
    TIMER("timer", "加速", "发包频率异常"),
    NOFALL("nofall", "摔落", "掉落伤害绕过"),
    JESUS("jesus", "水上行走", "液体上行走/悬浮"),
    SPIDER("spider", "爬墙", "贴墙攀爬"),
    BLINK("blink", "瞬移", "大位移瞬移"),
    ELYTRA("elytra", "鞘翅", "鞘翅加速"),

    // 战斗检测
    KILLAURA("killaura", "自瞄", "杀戮光环/自瞄"),
    AIM("aim", "视角", "视角旋转异常(瞄准机器)"),
    REACH("reach", "距离", "攻击距离超限"),
    CRITICALS("criticals", "暴击", "异常暴击"),
    AUTOCLICKER("autoclicker", "连点", "自动点击"),
    VELOCITY("velocity", "击退", "防击退"),

    // 世界/交互检测
    XRAY("xray", "透视", "透视挖矿"),
    NUKER("nuker", "范围挖掘", "快速破坏/范围挖掘"),
    SCAFFOLD("scaffold", "搭路", "快速放置/搭路"),
    FASTUSE("fastuse", "速用", "快速使用物品"),
    CHAT("chat", "刷屏", "聊天刷屏");

    private final String id;
    private final String displayName;
    private final String description;

    CheckType(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** 根据配置中的 id 查找枚举 */
    public static CheckType fromId(String id) {
        for (CheckType t : values()) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    /** 所有检测器 id 列表(用于命令补全) */
    public static String[] allIds() {
        CheckType[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
}
