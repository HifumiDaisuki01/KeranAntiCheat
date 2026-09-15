package com.keran.client.net;

import com.google.gson.JsonObject;
import com.keran.client.KeranClientMod;
import com.keran.client.config.ClientConfig;
import com.keran.client.report.ClientReport;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 上报与心跳发送器。
 * 频道: kac:report(详细指纹) / kac:heartbeat(存活心跳)
 */
public final class ReportSender {

    public static final Identifier REPORT_CHANNEL = new Identifier("kac", "report");
    public static final Identifier HEARTBEAT_CHANNEL = new Identifier("kac", "heartbeat");
    public static final Identifier DETAIL_CHANNEL = new Identifier("kac", "detail");
    public static final Identifier FILES_CHANNEL = new Identifier("kac", "files");

    private static ScheduledExecutorService heartbeatExecutor;
    private static ClientConfig activeConfig;
    private static long lastSendTime;

    private ReportSender() {
    }

    /** 发送完整指纹报告 */
    public static void sendReport() {
        try {
            if (MinecraftClient.getInstance().getNetworkHandler() == null) {
                return;
            }
            String player = MinecraftClient.getInstance().getSession() != null
                    ? MinecraftClient.getInstance().getSession().getUsername() : "";
            JsonObject report = ClientReport.buildReport(KeranClientMod.getConfig(), player);
            send(REPORT_CHANNEL, report.toString());
            lastSendTime = System.currentTimeMillis();
            KeranClientMod.getLogger().info("已向服务器发送指纹报告");
        } catch (Throwable t) {
            KeranClientMod.getLogger().warn("发送指纹报告失败: " + t.getMessage());
        }
    }

    /** 发送心跳 */
    public static void sendHeartbeat() {
        try {
            if (MinecraftClient.getInstance().getNetworkHandler() == null) {
                return;
            }
            String player = MinecraftClient.getInstance().getSession() != null
                    ? MinecraftClient.getInstance().getSession().getUsername() : "";
            JsonObject hb = ClientReport.buildHeartbeat(player);
            send(HEARTBEAT_CHANNEL, hb.toString());
        } catch (Throwable t) {
            KeranClientMod.getLogger().warn("发送心跳失败: " + t.getMessage());
        }
    }

    /** 启动心跳循环 (cfg 为 null 时仅心跳不报详细指纹) */
    public static void startHeartbeat(ClientConfig cfg) {
        stopHeartbeat();
        activeConfig = cfg;
        int seconds = cfg != null ? Math.max(5, cfg.heartbeatSeconds) : 30;
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KeranClient-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(ReportSender::sendHeartbeat, seconds, seconds, TimeUnit.SECONDS);
        // 白名单服务器且尚未发送过报告则补发
        if (cfg != null && lastSendTime == 0) {
            heartbeatExecutor.schedule(ReportSender::sendReport, 2, TimeUnit.SECONDS);
        }
    }

    public static void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        activeConfig = null;
    }

    /** 发送任意 JSON 到指定频道(客户端 -> 服务端) */
    public static void sendJson(Identifier channel, String json) {
        try {
            if (MinecraftClient.getInstance().getNetworkHandler() == null) {
                return;
            }
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(json);
            ClientPlayNetworking.send(channel, buf);
        } catch (Throwable t) {
            KeranClientMod.getLogger().warn("发送到 " + channel + " 失败: " + t.getMessage());
        }
    }

    private static void send(Identifier channel, String json) {
        sendJson(channel, json);
    }
}
