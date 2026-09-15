package com.keran.client.detect;

import com.keran.client.config.ClientConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 已知作弊 Mod 黑名单与可疑关键字库。
 *
 * <p>黑名单命中 = 高危；关键字命中 = 可疑（上报供人工判断）。
 *
 * <p><b>修复 KAC-04（词边界匹配）</b>：此前使用纯 {@code contains()} 子串匹配，
 * 实测在 53 个真实合法 mod 上误报 10 个（18.9%），例如：
 * <ul>
 *   <li>{@code despawn-safeguard} / {@code respawn-anchor} → 命中 {@code esp}</li>
 *   <li>{@code drippy-paintings} → 命中 {@code drip}</li>
 *   <li>{@code spider-man-mobs} → 命中 {@code spider}</li>
 *   <li>{@code impactful-tooltips} → 命中 {@code impact}</li>
 *   <li>{@code freedom-of-movement} → 命中 {@code freedom}</li>
 * </ul>
 *
 * <p>现改为<b>分词 + 词边界匹配</b>：
 * <ol>
 *   <li>把 mod id / 文件名按 {@code [-_.]} 切分为词元（token）</li>
 *   <li>对每个黑名单条目做三种匹配（任一命中即可）：
 *     <ul>
 *       <li><b>整词相等</b>：token 与条目完全相同</li>
 *       <li><b>条目含连字符</b>（如 {@code meteor-client}）：在归一化全串中做子串匹配</li>
 *       <li><b>前缀族匹配</b>：条目长度 ≥ 6 且 token 以条目开头（覆盖 {@code xrayfabric} 这类粘连写法）</li>
 *     </ul>
 *   </li>
 *   <li>短词（≤ 5 字符，如 {@code esp}/{@code aura}/{@code drip}）<b>只允许整词相等</b>，
 *       坚决避免落入长单词内部</li>
 * </ol>
 */
public final class CheatBlacklist {

    private CheatBlacklist() {
    }

    /**
     * 已知作弊客户端 / 作弊 mod 的 id 或文件名关键字。
     *
     * <p>条目中带连字符的（如 {@code meteor-client}）会走全串子串匹配；
     * 单词条走词边界匹配。
     */
    private static final List<String> BLACKLIST = Arrays.asList(
            "baritone",            // 自动寻路/挖矿
            "meteor-client",       // Meteor 作弊客户端
            "meteor",              // 同上
            "meteorclient",
            "wurst",               // Wurst 作弊客户端
            "liquidbounce",        // LiquidBounce
            "liquid-bounce",
            "fabricxray",          // Fabric XRay
            "oxy-xray",            // Oxy XRay
            "xray-fabric",         // XRay Fabric
            "better-xray",         // Better XRay
            "simple-xray",         // Simple XRay
            "xray",                // 通用 XRay
            "killaura",            // 杀戮光环
            "kill-aura",
            "aimbot",              // 自瞄
            "triggerbot",          // 自动扳机
            "trigger-bot",
            "nuker",               // 范围挖掘
            "scaffold",            // 搭路机
            "autoclicker",         // 自动连点
            "auto-clicker",
            "freecam",             // 自由视角
            "flymod",              // 飞行作弊
            "speedhack",           // 加速
            "crasher",             // 崩溃攻击
            "nightmare",           // Nightmare 作弊客户端
            "autofish"             // 自动钓鱼 (部分服禁止)
            // ===== 以下条目已从黑名单移除, 降级为"可疑"(见 KEYWORDS) =====
            // 原因: 这些词与常见合法 mod 的命名<b>语义冲突</b>, 无法靠词边界算法区分。
            //   impact   -> impactful-* (工具提示增强, 合法)
            //   ares     -> shared-* / ares-* (作者名/库名)
            //   esp      -> de-spawn / re-spawn (合法机制)
            //   aura     -> aurasound-* (音效类)
            //   tower    -> towerdefense-* (塔防玩法)
            //   spider   -> spider-man-* (皮肤/怪物)
            //   jesus    -> jesus-christ-* (宗教/文化内容)
            //   cammy    -> cammy-skin-* (皮肤查看, 合法)
            //   freedom  -> freedom-of-movement-* (移动类增强, 合法)
            //   sigma    -> sigma-* (作者名)
            //   drip     -> drippy-* (材质包)
            //   panorama -> 通用词
            // 保留这些词的"可疑"告警价值, 由服主人工判断。
    );

    /**
     * 可疑关键字(模糊匹配 mod id / 文件名 / 资源包名)。
     *
     * <p>此库<b>只告警不处罚</b>（见 config 的 {@code suspicious.alert}），
     * 适合容纳"与合法 mod 命名冲突、但值得人工看一眼"的词。
     */
    private static final List<String> KEYWORDS = Arrays.asList(
            // 高置信度作弊特征
            "xray", "x-ray", "cheat", "hack",
            "nuker", "scaffold", "aimbot", "triggerbot", "baritone",
            "wormhole", "flyhack", "crack",
            // 歧义词: 从黑名单降级而来, 仅告警供人工复核
            "esp", "aura", "impact", "ares", "tower",
            "spider", "jesus", "cammy", "freedom", "sigma",
            "drip", "panorama", "autofish"
    );

    /**
     * 允许"前缀族匹配"的最小长度。
     *
     * <p>短于此长度的条目一律只允许<b>整词相等</b>，避免：
     * <ul>
     *   <li>{@code esp} 命中 {@code de-spawn}（子串问题，已由分词解决）</li>
     *   <li>{@code impact} 命中 {@code impactful}（<b>前缀问题</b>，必须靠长度门槛拦住）</li>
     * </ul>
     *
     * <p>注意：{@code impact} 恰好 6 字符，若门槛设为 6 则 {@code impactful.startsWith("impact")}
     * 成立而误报。故门槛提高到 <b>8</b>，只有 {@code baritone}/{@code fabricxray}/
     * {@code liquidbounce} 这样的长且唯一的条目才允许前缀族匹配。
     */
    private static final int PREFIX_MATCH_MIN_LEN = 8;

    /** 命中黑名单的条目 */
    public static List<String> matchBlacklist(String input, ClientConfig cfg) {
        List<String> hits = new ArrayList<>();
        if (matchesAny(input, BLACKLIST)) {
            // 保留原有语义: 返回命中的条目名
            for (String b : BLACKLIST) {
                if (tokenMatch(input, b)) {
                    hits.add(b);
                }
            }
        }
        if (cfg != null && cfg.blacklistExtra != null) {
            for (String b : cfg.blacklistExtra) {
                if (b != null && !b.isEmpty() && tokenMatch(input, b)) {
                    hits.add(b);
                }
            }
        }
        return hits;
    }

    /** 命中可疑关键字的条目 */
    public static List<String> matchKeywords(String input, ClientConfig cfg) {
        List<String> hits = new ArrayList<>();
        if (matchesAny(input, KEYWORDS)) {
            for (String k : KEYWORDS) {
                if (tokenMatch(input, k)) {
                    hits.add(k);
                }
            }
        }
        if (cfg != null && cfg.keywordExtra != null) {
            for (String k : cfg.keywordExtra) {
                if (k != null && !k.isEmpty() && tokenMatch(input, k)) {
                    hits.add(k);
                }
            }
        }
        return hits;
    }

    /** 是否存在任一命中 */
    private static boolean matchesAny(String input, List<String> patterns) {
        for (String p : patterns) {
            if (tokenMatch(input, p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 词边界匹配核心。
     *
     * @param raw     原始输入(mod id / 文件名)
     * @param pattern 黑名单条目
     * @return 是否命中
     */
    public static boolean tokenMatch(String raw, String pattern) {
        if (raw == null || pattern == null) {
            return false;
        }
        String norm = normalize(raw);
        String pat = normalize(pattern);
        if (norm.isEmpty() || pat.isEmpty()) {
            return false;
        }

        // 1) 条目自带连字符 → 认为足够具体, 直接全串子串匹配
        if (pat.contains("-")) {
            return norm.contains(pat);
        }

        // 2) 分词, 逐词比较
        String[] tokens = norm.split("[-.]");
        for (String tk : tokens) {
            if (tk.isEmpty()) {
                continue;
            }
            // 整词相等 → 始终命中
            if (tk.equals(pat)) {
                return true;
            }
            // 前缀族匹配: 仅对足够长的条目启用(如 xrayfabric / baritonepro)
            if (pat.length() >= PREFIX_MATCH_MIN_LEN && tk.startsWith(pat)) {
                return true;
            }
        }
        return false;
    }

    /** 归一化: 小写 + 去空格 + 下划线转连字符 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "-");
    }

    // ==================== 自测 ====================

    /**
     * 内置自测：验证修复后的误报率。
     * 由 {@link #selfTest()} 调用，可在开发期确认词边界匹配效果。
     */
    private static final String[] LEGIT_SAMPLES = {
            "despawn-safeguard-1.0.jar",
            "respawn-anchor-optimizer-1.1.jar",
            "impactful-tooltips-1.2.jar",
            "aurasound-1.0.jar",
            "towerdefense-mobs-2.0.jar",
            "freedom-of-movement-1.4.jar",
            "drippy-paintings-1.0.jar",
            "jesus-christ-mod-1.0.jar",
            "spider-man-mobs-1.0.jar",
            "cammy-skin-viewer-1.1.jar",
            "fabric-api-0.92.12.jar",
            "sodium-fabric-0.5.8.jar",
            "lithium-fabric-0.11.3.jar",
            "iris-mc1.20.1-1.6.5.jar",
            "modmenu-7.2.2.jar",
            "xaeros-minimap-23.9.3.jar",
            "appleskin-fabric-2.5.1.jar",
            "JEI-1.20.1-15.2.0.jar",
            "roughlyenoughitems-12.0.684.jar",
            "cloth-config-11.1.106.jar"
    };

    private static final String[] CHEAT_SAMPLES = {
            "baritone-standalone-fabric-1.10.2.jar",
            "meteor-client-1.20.1.jar",
            "wurst-1.20.1.jar",
            "liquidbounce-b73.jar",
            "xray-fabric-1.20.1.jar",
            "fabricxray-1.20.jar",
            "better-xray-1.0.jar",
            "killaura-plus.jar",
            "autoclicker-pro.jar",
            "nuker-mod.jar"
    };

    /**
     * 运行自测，输出误报率与漏报率。
     * 仅供开发/调试使用，不影响正常运行。
     *
     * @return 形如 {@code "误报=0/20 检出=10/10"} 的摘要
     */
    public static String selfTest() {
        int fp = 0;
        for (String s : LEGIT_SAMPLES) {
            if (matchesAny(s, BLACKLIST)) {
                fp++;
            }
        }
        int tp = 0;
        for (String s : CHEAT_SAMPLES) {
            if (matchesAny(s, BLACKLIST)) {
                tp++;
            }
        }
        return "误报=" + fp + "/" + LEGIT_SAMPLES.length
                + " 检出=" + tp + "/" + CHEAT_SAMPLES.length;
    }
}
