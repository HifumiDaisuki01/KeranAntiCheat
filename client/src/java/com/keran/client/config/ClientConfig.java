package com.keran.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.keran.client.KeranClientMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 客户端配置 (config/kacclient.json)。
 * 可配置：开关、上报间隔、服务器白名单、黑名单扩展、静默模式。
 */
public class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    /** 心跳间隔(秒) */
    public int heartbeatSeconds = 30;
    /** 进服后延迟上报(秒) */
    public int reportDelaySeconds = 3;
    /** 静默模式: 不进游戏聊天通知, 只写日志 */
    public boolean silentMode = false;
    /** 仅向白名单服务器发送详细指纹; 空 = 全部发送 */
    public List<String> serverWhitelist = new ArrayList<>();
    /** 额外黑名单 mod id */
    public List<String> blacklistExtra = new ArrayList<>();
    /** 额外可疑关键字 */
    public List<String> keywordExtra = new ArrayList<>();

    public static ClientConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("kacclient.json");
        ClientConfig cfg;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(reader, ClientConfig.class);
            } catch (IOException e) {
                KeranClientMod.getLogger().warn("读取配置失败, 使用默认配置: " + e.getMessage());
                cfg = new ClientConfig();
            }
        } else {
            cfg = new ClientConfig();
        }
        if (cfg == null) {
            cfg = new ClientConfig();
        }
        cfg.save(path);
        return cfg;
    }

    public void save() {
        save(FabricLoader.getInstance().getConfigDir().resolve("kacclient.json"));
    }

    private void save(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            KeranClientMod.getLogger().warn("保存配置失败: " + e.getMessage());
        }
    }

    /** 当前服务器地址是否在白名单内(无白名单=全部允许) */
    public boolean isAllowedServer(String address) {
        if (serverWhitelist == null || serverWhitelist.isEmpty()) {
            return true;
        }
        if (address == null) {
            return false;
        }
        String addr = address.toLowerCase(Locale.ROOT);
        for (String w : serverWhitelist) {
            if (w == null || w.isEmpty()) {
                continue;
            }
            if (addr.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public JsonArray toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("heartbeatSeconds", heartbeatSeconds);
        o.addProperty("reportDelaySeconds", reportDelaySeconds);
        o.addProperty("silentMode", silentMode);
        JsonArray wl = new JsonArray();
        if (serverWhitelist != null) {
            for (String s : serverWhitelist) {
                wl.add(s);
            }
        }
        o.add("serverWhitelist", wl);
        JsonArray be = new JsonArray();
        if (blacklistExtra != null) {
            for (String s : blacklistExtra) {
                be.add(s);
            }
        }
        o.add("blacklistExtra", be);
        JsonArray ke = new JsonArray();
        if (keywordExtra != null) {
            for (String s : keywordExtra) {
                ke.add(s);
            }
        }
        o.add("keywordExtra", ke);
        JsonArray arr = new JsonArray();
        arr.add(o);
        return arr;
    }

    public static String configJson() {
        return GSON.toJson(new ClientConfig());
    }

    public static JsonElement asJson() {
        return GSON.toJsonTree(new ClientConfig());
    }
}
