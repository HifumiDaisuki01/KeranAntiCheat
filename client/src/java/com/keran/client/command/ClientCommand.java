package com.keran.client.command;

import com.keran.client.KeranClientMod;
import com.keran.client.detect.ModScanner;
import com.keran.client.net.ReportSender;
import com.keran.client.report.ClientReport;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

/**
 * 客户端命令: /kacclient
 * 子命令: status / report / reload / on / off
 */
public final class ClientCommand {

    private ClientCommand() {
    }

    public static void register() {
        ClientCommandManager.DISPATCHER.register(literal("kacclient")
                .then(literal("status").executes(ClientCommand::status))
                .then(literal("report").executes(ClientCommand::report))
                .then(literal("reload").executes(ClientCommand::reload))
                .then(literal("on").executes(ctx -> setEnabled(true)))
                .then(literal("off").executes(ctx -> setEnabled(false)))
                .executes(ClientCommand::status));
    }

    private static int status(CommandContext<?> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean online = client.getNetworkHandler() != null;
        JsonObject report = ClientReport.buildReport(KeranClientMod.getConfig(),
                client.getSession() != null ? client.getSession().getUsername() : "");

        StringBuilder msg = new StringBuilder();
        msg.append("§8[§cKACClient§8] §fKeranClient v").append(KeranClientMod.MOD_VERSION).append("\n");
        msg.append(" §7状态: ").append(KeranClientMod.isEnabled() ? "§a开启" : "§c关闭").append("\n");
        msg.append(" §7游戏连接: ").append(online ? "§a在线" : "§c未连接").append("\n");
        msg.append(" §7已加载 mod 数: §e").append(report.getAsJsonArray("mods").size()).append("\n");
        msg.append(" §7黑名单命中: ").append(report.getAsJsonArray("blacklist_hits").size() > 0
                ? "§c" + report.getAsJsonArray("blacklist_hits").toString() : "§a无").append("\n");
        msg.append(" §7可疑条目: ").append(report.getAsJsonArray("suspicious").size() > 0
                ? "§e" + report.getAsJsonArray("suspicious").toString() : "§a无").append("\n");
        msg.append(" §7JVM注入: ").append(report.getAsJsonArray("jvm_agents").size() > 0
                ? "§c" + report.getAsJsonArray("jvm_agents").toString() : "§a无").append("\n");
        msg.append(" §7指纹: §f").append(safeSub(report.get("fingerprint").getAsString()));
        client.player.sendMessage(new LiteralText(msg.toString()), false);
        return 1;
    }

    private static String safeSub(String s) {
        if (s == null) {
            return "?";
        }
        return s.length() > 16 ? s.substring(0, 16) + "..." : s;
    }

    private static int report(CommandContext<?> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            client.player.sendMessage(new LiteralText("§c[KACClient] 未连接服务器, 无法上报"), false);
            return 0;
        }
        ReportSender.sendReport();
        client.player.sendMessage(new LiteralText("§a[KACClient] 指纹报告已发送"), false);
        return 1;
    }

    private static int reload(CommandContext<?> ctx) {
        KeranClientMod.reloadConfig();
        clientMsg("§a[KACClient] 配置已重新加载");
        return 1;
    }

    private static int setEnabled(boolean on) {
        KeranClientMod.setEnabled(on);
        clientMsg(on ? "§a[KACClient] 检测已开启" : "§e[KACClient] 检测已关闭");
        return 1;
    }

    private static void clientMsg(String s) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(new LiteralText(s), false);
        }
    }
}
