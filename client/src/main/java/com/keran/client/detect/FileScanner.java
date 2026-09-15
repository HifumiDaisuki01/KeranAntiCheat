package com.keran.client.detect;

import com.keran.client.KeranClientMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件扫描器：扫描目录内文件(名称/大小/MD5)。
 * 用于 mods 目录与材质包目录的详细清单与完整性校验。
 */
public final class FileScanner {

    private FileScanner() {
    }

    public static final class FileInfo {
        public final String name;
        public final long size;
        public final String md5;

        public FileInfo(String name, long size, String md5) {
            this.name = name;
            this.size = size;
            this.md5 = md5;
        }

        public JsonObject toJson(boolean withMd5) {
            JsonObject o = new JsonObject();
            o.addProperty("file", name);
            o.addProperty("size", size);
            if (withMd5 && md5 != null) {
                o.addProperty("md5", md5);
            }
            return o;
        }
    }

    /** 扫描 mods 目录下所有 jar (含子目录), 带大小与 MD5 */
    public static List<FileInfo> scanModsJars() {
        return scanDir(FabricLoader.getInstance().getGameDir().resolve("mods"), ".jar", true);
    }

    /** 扫描材质包目录下所有 zip (含子目录), 带大小与 MD5 */
    public static List<FileInfo> scanResourcePackZips() {
        return scanDir(FabricLoader.getInstance().getGameDir().resolve("resourcepacks"), ".zip", true);
    }

    /** 扫描目录, suffix 过滤后缀 */
    public static List<FileInfo> scanDir(Path dir, String suffix, boolean withMd5) {
        List<FileInfo> files = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return files;
        }
        collect(dir, suffix, withMd5, files);
        return files;
    }

    private static void collect(Path dir, String suffix, boolean withMd5, List<FileInfo> out) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    collect(p, suffix, withMd5, out);
                } else {
                    String name = p.getFileName().toString();
                    if (name.toLowerCase().endsWith(suffix)) {
                        long size = 0;
                        try {
                            size = Files.size(p);
                        } catch (IOException ignored) {
                        }
                        String md5 = withMd5 ? md5Of(p) : null;
                        out.add(new FileInfo(name, size, md5));
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** 计算文件 MD5 (流式, 大文件安全) */
    public static String md5Of(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            KeranClientMod.getLogger().warn("计算 MD5 失败 " + file + ": " + e.getMessage());
            return null;
        }
    }

    public static JsonArray toJson(List<FileInfo> files, boolean withMd5) {
        JsonArray arr = new JsonArray();
        for (FileInfo f : files) {
            arr.add(f.toJson(withMd5));
        }
        return arr;
    }

    // ===== 目录整体 MD5 预计算缓存 =====
    // 客户端启动时在后台线程计算, 进服上报时直接读取缓存, 避免进服时卡顿
    private static volatile String cachedModsDirMd5;
    private static volatile String cachedPacksDirMd5;

    /** 启动预计算: 后台线程扫描 mods/材质包目录并计算整体 MD5 */
    public static void precomputeDirMd5() {
        Thread t = new Thread(() -> {
            try {
                cachedModsDirMd5 = dirMd5(scanModsJars());
            } catch (Exception e) {
                KeranClientMod.getLogger().warn("预计算 mods 目录 MD5 失败: " + e.getMessage());
            }
            try {
                cachedPacksDirMd5 = dirMd5(scanResourcePackZips());
            } catch (Exception e) {
                KeranClientMod.getLogger().warn("预计算材质包目录 MD5 失败: " + e.getMessage());
            }
            KeranClientMod.getLogger().info("目录 MD5 预计算完成: mods="
                    + (cachedModsDirMd5 == null ? "?" : cachedModsDirMd5)
                    + " packs=" + (cachedPacksDirMd5 == null ? "?" : cachedPacksDirMd5));
        }, "KeranClient-DirMd5");
        t.setDaemon(true);
        t.start();
    }

    /** 获取 mods 目录整体 MD5 (优先缓存, 未就绪则同步计算) */
    public static String modsDirMd5Cached() {
        if (cachedModsDirMd5 == null) {
            cachedModsDirMd5 = dirMd5(scanModsJars());
        }
        return cachedModsDirMd5;
    }

    /** 获取材质包目录整体 MD5 (优先缓存, 未就绪则同步计算) */
    public static String packsDirMd5Cached() {
        if (cachedPacksDirMd5 == null) {
            cachedPacksDirMd5 = dirMd5(scanResourcePackZips());
        }
        return cachedPacksDirMd5;
    }

    /**
     * 计算目录整体指纹 MD5: 对目录下所有文件(名称+大小+各自MD5)按名称排序拼接后取 MD5。
     * 任一文件变化都会导致目录指纹改变, 便于服务端快速比对。
     */
    public static String dirMd5(List<FileInfo> files) {
        try {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (FileInfo f : files) {
                parts.add(f.name + "|" + f.size + "|" + (f.md5 == null ? "" : f.md5));
            }
            java.util.Collections.sort(parts);
            StringBuilder sb = new StringBuilder();
            for (String s : parts) {
                sb.append(s).append(';');
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return "ERR";
        }
    }
}
