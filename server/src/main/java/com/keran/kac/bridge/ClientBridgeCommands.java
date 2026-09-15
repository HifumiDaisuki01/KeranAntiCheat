package com.keran.kac.bridge;

import com.keran.kac.bridge.ClientData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端桥接相关命令逻辑(整合进 /kac 主命令):
 * cinfo / detail / modsmd5 / packsmd5 / list
 */
public class ClientBridgeCommands {

    private final ClientBridgeModule module;

    public ClientBridgeCommands(ClientBridgeModule module) {
        this.module = module;
    }

    private void send(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', ClientBridgeModule.PREFIX + msg));
    }

    /** 返回 true 表示已处理该子命令 */
    public boolean handle(CommandSender sender, String[] args) {
        switch (args[0].toLowerCase()) {
            case "cinfo":
                info(sender, args);
                return true;
            case "detail":
                queryDetail(sender, args);
                return true;
            case "modsmd5":
                queryModsMd5(sender, args);
                return true;
            case "packsmd5":
                queryPacksMd5(sender, args);
                return true;
            case "list":
                list(sender);
                return true;
            default:
                return false;
        }
    }

    /** 第一层子命令列表(供主命令 Tab 补全合并) */
    public static final List<String> SUB_COMMANDS = java.util.Arrays.asList(
            "cinfo", "detail", "modsmd5", "packsmd5", "list");

    /** Tab 补全(玩家名) */
    public List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length >= 2 && ("cinfo".equalsIgnoreCase(args[0])
                || "detail".equalsIgnoreCase(args[0])
                || "modsmd5".equalsIgnoreCase(args[0])
                || "packsmd5".equalsIgnoreCase(args[0]))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac cinfo <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        ClientData d = module.getClientData(target);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c客户端指纹: §e" + target.getName() + " §8====="));
        if (d == null || !d.hasReported()) {
            send(sender, "§7该玩家尚未上报 KeranClient 指纹(可能未安装)");
            return;
        }
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f已加载 mod 数: §e" + d.modCount);
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f指纹: §e" + (d.fingerprint.length() > 20 ? d.fingerprint.substring(0, 20) + "..." : d.fingerprint));
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f黑名单命中: " + (d.blacklistHits.isEmpty() ? "§a无" : "§c" + d.blacklistHits));
        if (!d.blacklistSources.isEmpty()) {
            sender.sendMessage(ClientBridgeModule.PREFIX + "§f命中来源: §7" + d.blacklistSources);
        }
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f可疑条目: " + (d.suspicious.isEmpty() ? "§a无" : "§e" + d.suspicious));
        sender.sendMessage(ClientBridgeModule.PREFIX + "§fJVM注入: " + (d.jvmAgents.isEmpty() ? "§a无" : "§c" + d.jvmAgents));
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f可疑资源包: " + (d.suspiciousPacks.isEmpty() ? "§a无" : "§e" + d.suspiciousPacks));
        sender.sendMessage(ClientBridgeModule.PREFIX + "§f最后心跳: §7" + (System.currentTimeMillis() - d.lastHeartbeat) / 1000 + " 秒前");
        sender.sendMessage(ClientBridgeModule.PREFIX + "§7提示: /kac detail " + target.getName() + " §7查看详细清单");
    }

    private void queryDetail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac detail <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        ClientData d = module.getClientData(target);
        if (d == null || !d.hasReported()) {
            send(sender, "§c该玩家未上报指纹(未安装 KeranClient 或尚未上报)");
            return;
        }
        // 客户端连入已自动上报全量清单, 直接显示缓存
        module.sendDetailTo(sender, target, d);
    }

    private void queryModsMd5(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac modsmd5 <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        ClientData d = module.getClientData(target);
        if (d == null || !d.hasReported()) {
            send(sender, "§c该玩家未上报指纹(未安装 KeranClient 或尚未上报)");
            return;
        }
        module.sendFilesTo(sender, target, d);
    }

    private void queryPacksMd5(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac packsmd5 <玩家>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        ClientData d = module.getClientData(target);
        if (d == null || !d.hasReported()) {
            send(sender, "§c该玩家未上报指纹(未安装 KeranClient 或尚未上报)");
            return;
        }
        module.sendPackMd5To(sender, target, d);
    }

    private void list(CommandSender sender) {
        Map<UUID, ClientData> map = module.getClientData();
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c已上报玩家 (" + map.size() + ") §8====="));
        if (map.isEmpty()) {
            send(sender, "§7暂无玩家上报");
            return;
        }
        for (ClientData d : map.values()) {
            String flag = d.blacklistHits.isEmpty() ? "§a正常" : "§c作弊:" + d.blacklistHits;
            sender.sendMessage(ClientBridgeModule.PREFIX + "§e" + d.name + " §7mods=" + d.modCount + " " + flag);
        }
    }
}
