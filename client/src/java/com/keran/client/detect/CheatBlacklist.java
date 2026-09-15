package com.keran.client.detect;

import com.keran.client.config.ClientConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 已知作弊 Mod 黑名单与可疑关键字库。
 * 黑名单命中 = 高危; 关键字命中 = 可疑(上报供人工判断)。
 */
public final class CheatBlacklist {

    private CheatBlacklist() {
    }

    /** 已知作弊客户端 / 作弊 mod 的 id 或文件名关键字 (1.16 常见) */
    private static final List<String> BLACKLIST = Arrays.asList(
            "baritone",            // 自动寻路/挖矿
            "meteor-client",       // Meteor 作弊客户端
            "meteor",              // 同上
            "impact",              // Impact 作弊客户端
            "wurst",               // Wurst 作弊客户端
            "liquidbounce",        // LiquidBounce
            "ares",                // Ares 作弊客户端
            "fabricxray",          // Fabric XRay
            "oxy-xray",            // Oxy XRay
            "xray-fabric",         // XRay Fabric
            "better-xray",         // Better XRay
            "simple-xray",         // Simple XRay
            "xray",                // 通用 XRay
            "esp",                 // ESP(透视实体)
            "killaura",            // 杀戮光环
            "aura",                // 光环类
            "aimbot",              // 自瞄
            "triggerbot",          // 自动扳机
            "nuker",               // 范围挖掘
            "scaffold",            // 搭路机
            "tower",               // 一键上塔
            "autoclicker",         // 自动连点
            "autofish",            // 自动钓鱼(部分服禁止)
            "freecam",             // 自由视角
            "spider",              // 爬墙
            "jesus",               // 水上行走
            "flymod",              // 飞行作弊
            "speedhack",           // 加速
            "crasher",             // 崩溃攻击
            "panorama",            // 作弊客户端库
            "cammy",               // 作弊客户端库
            "freedom",             // Freedom 作弊
            "sigma",               // Sigma 作弊客户端
            "nightmare",           // Nightmare 作弊客户端
            "drip"                 // Drip 作弊客户端
    );

    /** 可疑关键字(模糊匹配 mod id / 文件名 / 资源包名) */
    private static final List<String> KEYWORDS = Arrays.asList(
            "xray", "x-ray", "esp", "aura", "cheat", "hack",
            "nuker", "scaffold", "aimbot", "triggerbot", "baritone",
            "freemine", "wormhole", "flyhack", "teleport", "crack"
    );

    /** 命中黑名单的条目 */
    public static List<String> matchBlacklist(String input, ClientConfig cfg) {
        List<String> hits = new ArrayList<>();
        String s = normalize(input);
        if (s.isEmpty()) {
            return hits;
        }
        for (String b : BLACKLIST) {
            if (s.contains(b)) {
                hits.add(b);
            }
        }
        if (cfg != null && cfg.blacklistExtra != null) {
            for (String b : cfg.blacklistExtra) {
                if (b != null && !b.isEmpty() && s.contains(normalize(b))) {
                    hits.add(b);
                }
            }
        }
        return hits;
    }

    /** 命中可疑关键字的条目 */
    public static List<String> matchKeywords(String input, ClientConfig cfg) {
        List<String> hits = new ArrayList<>();
        String s = normalize(input);
        if (s.isEmpty()) {
            return hits;
        }
        for (String k : KEYWORDS) {
            if (s.contains(k)) {
                hits.add(k);
            }
        }
        if (cfg != null && cfg.keywordExtra != null) {
            for (String k : cfg.keywordExtra) {
                if (k != null && !k.isEmpty() && s.contains(normalize(k))) {
                    hits.add(k);
                }
            }
        }
        return hits;
    }

    /** 归一化: 小写 + 去空格 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "-");
    }
}
