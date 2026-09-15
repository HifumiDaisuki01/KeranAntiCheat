package com.keran.client.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.keran.client.config.ClientConfig;
import com.keran.client.detect.CheatBlacklist;
import com.keran.client.detect.EnvScanner;
import com.keran.client.detect.FileScanner;
import com.keran.client.detect.ModScanner;
import com.keran.client.detect.ResourcePackScanner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * 客户端指纹报告生成。
 * 报告包含: mod 清单、黑名单命中、可疑条目、JVM 注入检测、环境信息、资源包、指纹哈希。
 */
public final class ClientReport {

    private ClientReport() {
    }

    /** 生成完整报告 JSON */
    public static JsonObject buildReport(ClientConfig cfg, String playerName) {
        JsonObject root = new JsonObject();

        // 基础
        root.addProperty("type", "report");
        root.addProperty("v", "1.0.0");
        root.addProperty("player", playerName == null ? "" : playerName);
        root.addProperty("ts", System.currentTimeMillis());

        // mod 清单
        JsonArray mods = ModScanner.loadedModsJson();
        root.add("mods", mods);

        // 黑名单 / 可疑检测
        ModScanner.DetectResult detect = ModScanner.detect(cfg);
        JsonArray hits = new JsonArray();
        for (String h : detect.blacklistHits) {
            hits.add(h);
        }
        root.add("blacklist_hits", hits);
        JsonArray hitSources = new JsonArray();
        for (String s : detect.blacklistSources) {
            hitSources.add(s);
        }
        root.add("blacklist_sources", hitSources);

        JsonArray suspicious = new JsonArray();
        for (String s : detect.suspiciousHits) {
            suspicious.add(s);
        }
        root.add("suspicious", suspicious);
        JsonArray susSources = new JsonArray();
        for (String s : detect.suspiciousSources) {
            susSources.add(s);
        }
        root.add("suspicious_sources", susSources);

        // JVM 注入检测
        List<String> agents = EnvScanner.detectJvmAgents();
        List<String> suspArgs = EnvScanner.detectSuspiciousJvmArgs();
        JsonArray jvmHits = new JsonArray();
        for (String a : agents) {
            jvmHits.add(a);
        }
        root.add("jvm_agents", jvmHits);
        JsonArray suspJvm = new JsonArray();
        for (String a : suspArgs) {
            suspJvm.add(a);
        }
        root.add("suspicious_jvm_args", suspJvm);

        // 环境
        root.add("env", EnvScanner.envJson());

        // 资源包
        JsonArray packs = ResourcePackScanner.enabledPacksJson();
        root.add("resourcepacks", packs);
        List<String> badPacks = ResourcePackScanner.detectSuspiciousPacks();
        JsonArray badPacksArr = new JsonArray();
        for (String p : badPacks) {
            badPacksArr.add(p);
        }
        root.add("suspicious_packs", badPacksArr);

        // mods 目录文件名清单(便于人工核查)
        JsonArray dirFiles = new JsonArray();
        for (String f : ModScanner.scanModsDirectory()) {
            dirFiles.add(f);
        }
        root.add("mods_dir", dirFiles);

        // ===== 全量文件清单(连入自动上报, 无需服务端查询) =====
        // mods 目录文件: 名称 + 大小 + MD5
        List<FileScanner.FileInfo> modFiles = FileScanner.scanModsJars();
        root.add("mod_files", FileScanner.toJson(modFiles, true));
        // 材质包目录文件: 名称 + 大小 + MD5
        List<FileScanner.FileInfo> packFiles = FileScanner.scanResourcePackZips();
        root.add("pack_files", FileScanner.toJson(packFiles, true));
        // 目录整体指纹(客户端启动时预计算缓存, 任一文件变化都会改变)
        root.addProperty("mods_dir_md5", FileScanner.modsDirMd5Cached());
        root.addProperty("packs_dir_md5", FileScanner.packsDirMd5Cached());

        // 指纹哈希: 基于 mod 列表 + 关键环境, 防简单篡改
        root.addProperty("fingerprint", computeFingerprint(mods));

        return root;
    }

    /** 生成心跳 JSON */
    public static JsonObject buildHeartbeat(String playerName) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "heartbeat");
        o.addProperty("v", "1.0.0");
        o.addProperty("player", playerName == null ? "" : playerName);
        o.addProperty("ts", System.currentTimeMillis());
        return o;
    }

    /**
     * 生成详细清单(响应服务端查询):
     * 已加载 mod(简略) + mods 目录文件(名称/大小) + 材质包文件(名称/大小)
     */
    public static JsonObject buildDetailReport(String playerName) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "detail");
        root.addProperty("v", "1.0.0");
        root.addProperty("player", playerName == null ? "" : playerName);
        root.addProperty("ts", System.currentTimeMillis());

        // 已加载 mod (id/name/version)
        root.add("mods", ModScanner.loadedModsJson());

        // mods 目录文件(名称+大小)
        root.add("mod_files", FileScanner.toJson(FileScanner.scanModsJars(), false));

        // 材质包文件(名称+大小)
        root.add("pack_files", FileScanner.toJson(FileScanner.scanResourcePackZips(), false));

        return root;
    }

    /**
     * 生成 MD5 校验报告(响应服务端查询):
     * mods 目录 + 材质包目录所有文件的 名称/MD5/大小
     */
    public static JsonObject buildFilesReport(String playerName) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "files");
        root.addProperty("v", "1.0.0");
        root.addProperty("player", playerName == null ? "" : playerName);
        root.addProperty("ts", System.currentTimeMillis());

        root.add("mod_files", FileScanner.toJson(FileScanner.scanModsJars(), true));
        root.add("pack_files", FileScanner.toJson(FileScanner.scanResourcePackZips(), true));

        return root;
    }

    /** 计算指纹: 已加载 mod 的 id+version 排序后 SHA-256 */
    public static String computeFingerprint(JsonArray mods) {
        try {
            StringBuilder sb = new StringBuilder();
            java.util.List<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < mods.size(); i++) {
                JsonObject o = mods.get(i).getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "?";
                String ver = o.has("version") ? o.get("version").getAsString() : "?";
                list.add(id + "@" + ver);
            }
            java.util.Collections.sort(list);
            for (String s : list) {
                sb.append(s).append(';');
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return "ERR";
        }
    }
}
