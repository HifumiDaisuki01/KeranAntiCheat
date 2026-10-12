package com.keran.client.net;

import com.google.gson.JsonObject;
import com.keran.client.KeranClientMod;
import com.keran.client.config.ClientConfig;
import com.keran.client.report.ClientReport;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.concurrent.*;

/** A worker owns scanning; all network/client access runs on the game thread. */
public final class ReportSender {
    public static final Identifier REPORT_CHANNEL = new Identifier("kac", "report");
    public static final Identifier HEARTBEAT_CHANNEL = new Identifier("kac", "heartbeat");
    public static final Identifier DETAIL_CHANNEL = new Identifier("kac", "detail");
    public static final Identifier FILES_CHANNEL = new Identifier("kac", "files");
    private static ScheduledExecutorService worker;
    private static ClientPlayNetworkHandler connection;
    private static ClientConfig activeConfig;
    private static String nonce;
    private static long epoch;
    private static boolean reportPending;
    private static boolean reportSent;
    private static long lastQuery;
    private ReportSender() {}

    public static void startHeartbeat(ClientConfig cfg) {
        stopHeartbeat();
        MinecraftClient client = MinecraftClient.getInstance();
        connection = client.getNetworkHandler();
        activeConfig = cfg;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KeranClient-Scanner"); t.setDaemon(true); return t;
        });
        long session = epoch;
        int seconds = Math.max(5, Math.min(120, cfg.heartbeatSeconds));
        worker.scheduleAtFixedRate(() -> client.execute(() -> {
            if (epoch == session) sendHeartbeat();
        }), seconds, seconds, TimeUnit.SECONDS);
    }

    public static void stopHeartbeat() {
        epoch++;
        if (worker != null) worker.shutdownNow();
        worker = null; connection = null; activeConfig = null; nonce = null;
        reportPending = false; reportSent = false; lastQuery = 0;
    }

    private static boolean allowed() {
        return worker != null && connection != null && activeConfig != null
                && activeConfig.isAllowedServer(connection.getConnection().getAddress().toString());
    }

    public static void acceptChallenge(String challenge) {
        if (!allowed() || !challenge.matches("[0-9a-fA-F-]{36}")) return;
        if (!challenge.equals(nonce)) { nonce = challenge; reportSent = false; }
        if (!reportSent && !reportPending) sendReport();
    }

    public static void reloadPolicy(ClientConfig cfg) { activeConfig = cfg; }

    public static void sendReport() {
        if (!allowed() || nonce == null || reportPending) return;
        reportPending = true;
        submit(REPORT_CHANNEL, true, false, null);
    }

    public static void respond(String query) {
        if (!allowed() || nonce == null || !reportSent) return;
        int colon = query.indexOf(':');
        if (colon < 0) return;
        String type = query.substring(0, colon), requestId = query.substring(colon + 1);
        if (!requestId.matches("[0-9a-fA-F-]{36}")) return;
        long now = System.nanoTime();
        if (lastQuery != 0 && now - lastQuery < TimeUnit.SECONDS.toNanos(2)) return;
        lastQuery = now;
        if ("detail".equalsIgnoreCase(type)) submit(DETAIL_CHANNEL, false, false, requestId);
        else if ("files".equalsIgnoreCase(type) || "modsmd5".equalsIgnoreCase(type) || "packsmd5".equalsIgnoreCase(type))
            submit(FILES_CHANNEL, false, true, requestId);
    }

    private static void submit(Identifier channel, boolean report, boolean files, String requestId) {
        MinecraftClient client = MinecraftClient.getInstance();
        long session = epoch;
        String token = nonce;
        ClientConfig cfg = activeConfig;
        String player = client.getSession().getUsername();
        com.google.gson.JsonArray packs = report ? com.keran.client.detect.ResourcePackScanner.enabledPacksJson() : new com.google.gson.JsonArray();
        java.util.List<String> badPacks = report ? com.keran.client.detect.ResourcePackScanner.detectSuspiciousPacks() : java.util.List.of();
        worker.execute(() -> {
            try {
                JsonObject obj = report ? ClientReport.buildReport(cfg, player, packs, badPacks)
                        : files ? ClientReport.buildFilesReport(player) : ClientReport.buildDetailReport(player);
                obj.addProperty("nonce", token);
                if (requestId != null) obj.addProperty("request_id", requestId);
                String json = obj.toString();
                client.execute(() -> {
                    if (epoch != session || connection != client.getNetworkHandler()) return;
                    if (!allowed() || !token.equals(nonce)) { if (report) reportPending = false; return; }
                    boolean sent = sendNow(channel, json);
                    if (report) { reportPending = false; reportSent = sent; }
                });
            } catch (Exception error) {
                KeranClientMod.getLogger().warn("扫描/上报失败: " + error.getMessage());
                client.execute(() -> { if (epoch == session && report) reportPending = false; });
            }
        });
    }

    public static void sendHeartbeat() {
        if (!allowed() || nonce == null || !reportSent) return;
        JsonObject obj = ClientReport.buildHeartbeat(MinecraftClient.getInstance().getSession().getUsername());
        obj.addProperty("nonce", nonce);
        sendNow(HEARTBEAT_CHANNEL, obj.toString());
    }

    public static void sendJson(Identifier channel, String json) {
        MinecraftClient.getInstance().execute(() -> { if (allowed()) sendNow(channel, json); });
    }

    private static boolean sendNow(Identifier channel, String json) {
        if (connection != MinecraftClient.getInstance().getNetworkHandler() || !ClientPlayNetworking.canSend(channel)) return false;
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 30000) {
            KeranClientMod.getLogger().warn("上报超过30000字节，请精简整合包或文件清单"); return false;
        }
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(json); ClientPlayNetworking.send(channel, buf); return true;
        } catch (RuntimeException error) {
            KeranClientMod.getLogger().warn("发送失败: " + error.getMessage()); return false;
        }
    }
}
