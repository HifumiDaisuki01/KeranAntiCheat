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

    private static final Identifier QUERY_CHANNEL = Identifier.of("kac", "query");

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
            client.execute(() -> handle(client, ftype));
        });
    }

    private static void handle(MinecraftClient client, String type) {
        String player = client.getSession() != null ? client.getSession().getUsername() : "";
        if ("detail".equalsIgnoreCase(type)) {
            ReportSender.sendJson(ReportSender.DETAIL_CHANNEL, ClientReport.buildDetailReport(player).toString());
            KeranClientMod.getLogger().info("已响应服务端查询: 详细 mods/材质包清单");
        } else if ("modsmd5".equalsIgnoreCase(type) || "packsmd5".equalsIgnoreCase(type)
                || "files".equalsIgnoreCase(type)) {
            ReportSender.sendJson(ReportSender.FILES_CHANNEL, ClientReport.buildFilesReport(player).toString());
            KeranClientMod.getLogger().info("已响应服务端查询: MD5 校验清单(" + type + ")");
        } else {
            KeranClientMod.getLogger().warn("未知查询类型: " + type);
        }
    }
}
