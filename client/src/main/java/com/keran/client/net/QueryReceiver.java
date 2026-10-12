package com.keran.client.net;

import com.keran.client.KeranClientMod;
import com.keran.client.report.ClientReport;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * 服务端查询指令接收器。
 * 频道: kac:query (服务端 -> 客户端)
 *   type=detail → 回复详细 mods/材质包清单(含文件大小)
 *   type=files  → 回复 mods 目录与材质包目录的 MD5 清单
 */
public final class QueryReceiver {

    private static final Identifier QUERY_CHANNEL = new Identifier("kac", "query");

    private QueryReceiver() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(QUERY_CHANNEL, (client, handler, buf, responseSender) -> {
            String type;
            try {
                type = buf.readString();
            } catch (Exception e) {
                KeranClientMod.getLogger().warn("解析查询指令失败: " + e.getMessage());
                return;
            }
            final String ftype = type;
            // 切到客户端主线程执行(文件扫描涉及 IO)
            client.execute(() -> { if (client.getNetworkHandler() == handler) handle(client, ftype); });
        });
    }

    private static void handle(MinecraftClient client, String type) {
        if (type.startsWith("challenge:")) {
            ReportSender.acceptChallenge(type.substring(10));
            return;
        }
        ReportSender.respond(type);
    }
}
