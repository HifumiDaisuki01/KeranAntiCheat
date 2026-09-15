package com.keran.client.detect;

import com.google.gson.JsonArray;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/**
 * 资源包扫描：检测启用中的资源包是否含 XRay/ESP/Hack 等透视纹理特征。
 */
public final class ResourcePackScanner {

    private ResourcePackScanner() {
    }

    /** 已启用资源包 id 列表 */
    public static JsonArray enabledPacksJson() {
        JsonArray arr = new JsonArray();
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ResourcePackManager manager = client.getResourcePackManager();
            if (manager != null) {
                Collection<ResourcePackProfile> profiles = manager.getEnabledProfiles();
                for (ResourcePackProfile p : profiles) {
                    arr.add(p.getDisplayName().getString());
                }
            }
        } catch (Throwable ignored) {
        }
        return arr;
    }

    /** 返回含可疑关键字的资源包 id */
    public static List<String> detectSuspiciousPacks() {
        List<String> hits = new ArrayList<>();
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ResourcePackManager manager = client.getResourcePackManager();
            if (manager != null) {
                for (ResourcePackProfile profile : manager.getEnabledProfiles()) {
                    String id = profile.getDisplayName().getString();
                    String low = id.toLowerCase(Locale.ROOT);
                    if (low.contains("xray") || low.contains("x-ray")
                            || low.contains("esp") || low.contains("hack")
                            || low.contains("cheat") || low.contains("ore")) {
                        hits.add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return hits;
    }
}
