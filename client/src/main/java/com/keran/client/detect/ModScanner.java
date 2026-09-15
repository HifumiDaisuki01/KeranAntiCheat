package com.keran.client.detect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.keran.client.config.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模组扫描器：
 * 1) FabricLoader 已加载的全部 mod(id/版本/名称)
 * 2) mods 目录内所有 .jar 文件名(含未加载的), 用于发现"放在目录里但没被加载"的作弊 mod
 * 对两者都执行黑名单/关键字匹配。
 */
public final class ModScanner {

    private ModScanner() {
    }

    /** 已加载 mod 列表(JSON) */
    public static JsonArray loadedModsJson() {
        JsonArray arr = new JsonArray();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = mod.getMetadata();
            JsonObject o = new JsonObject();
            o.addProperty("id", meta.getId());
            o.addProperty("name", meta.getName());
            o.addProperty("version", meta.getVersion().getFriendlyString());
            arr.add(o);
        }
        return arr;
    }

    /** 已加载 mod 的 id 集合 */
    public static List<String> loadedModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId())
                .collect(Collectors.toList());
    }

    /** 扫描 mods 目录中的 jar 文件名 */
    public static List<String> scanModsDirectory() {
        List<String> files = new ArrayList<>();
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (Files.isDirectory(modsDir)) {
            collectJars(modsDir, files);
        }
        return files;
    }

    private static void collectJars(Path dir, List<String> out) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    collectJars(p, out);
                } else {
                    String name = p.getFileName().toString();
                    if (name.toLowerCase().endsWith(".jar")) {
                        out.add(name);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** 检测结果: 返回 {hits:[], suspicious:[], source} */
    public static DetectResult detect(ClientConfig cfg) {
        DetectResult result = new DetectResult();

        // 1) 已加载 mod
        for (String id : loadedModIds()) {
            String n = CheatBlacklist.normalize(id);
            result.addBlacklist(CheatBlacklist.matchBlacklist(n, cfg), "loaded:" + id);
            result.addSuspicious(CheatBlacklist.matchKeywords(n, cfg), "loaded:" + id);
        }

        // 2) mods 目录文件
        for (String file : scanModsDirectory()) {
            String n = CheatBlacklist.normalize(file);
            result.addBlacklist(CheatBlacklist.matchBlacklist(n, cfg), "file:" + file);
            result.addSuspicious(CheatBlacklist.matchKeywords(n, cfg), "file:" + file);
        }

        return result;
    }

    /** 检测结果容器 */
    public static class DetectResult {
        public final List<String> blacklistHits = new ArrayList<>();
        public final List<String> suspiciousHits = new ArrayList<>();
        public final List<String> blacklistSources = new ArrayList<>();
        public final List<String> suspiciousSources = new ArrayList<>();

        void addBlacklist(List<String> hits, String source) {
            for (String h : hits) {
                if (!blacklistHits.contains(h)) {
                    blacklistHits.add(h);
                    blacklistSources.add(source);
                }
            }
        }

        void addSuspicious(List<String> hits, String source) {
            for (String h : hits) {
                if (!suspiciousHits.contains(h)) {
                    suspiciousHits.add(h);
                    suspiciousSources.add(source);
                }
            }
        }

        public boolean hasBlacklist() {
            return !blacklistHits.isEmpty();
        }

        public boolean hasSuspicious() {
            return !suspiciousHits.isEmpty();
        }
    }
}
