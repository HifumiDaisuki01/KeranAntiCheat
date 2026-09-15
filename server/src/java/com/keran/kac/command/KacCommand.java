package com.keran.kac.command;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.BanManager;
import com.keran.kac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /kac 命令系统与 Tab 补全。
 */
public class KacCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "§8[§cKAC§8] §7");

    private final KeranAntiCheat plugin;

    public KacCommand(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    private boolean hasAdmin(CommandSender sender) {
        if (sender.hasPermission("kac.admin") || sender.isOp()) {
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.RED + "你没有权限使用该命令 (需要 kac.admin)");
        return false;
    }

    private void send(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                help(sender);
                break;
            case "reload":
                if (!hasAdmin(sender)) return true;
                plugin.getConfigManager().reload();
                plugin.getBanManager().load();
                send(sender, "§a配置已重载");
                break;
            case "alerts":
                alerts(sender, args);
                break;
            case "info":
                if (!hasAdmin(sender)) return true;
                info(sender, args);
                break;
            case "reset":
                if (!hasAdmin(sender)) return true;
                reset(sender, args);
                break;
            case "checks":
                if (!hasAdmin(sender)) return true;
                checks(sender);
                break;
            case "toggle":
                if (!hasAdmin(sender)) return true;
                toggle(sender, args);
                break;
            case "ban":
                if (!hasAdmin(sender)) return true;
                ban(sender, args);
                break;
            case "unban":
                if (!hasAdmin(sender)) return true;
                unban(sender, args);
                break;
            case "kick":
                if (!hasAdmin(sender)) return true;
                kick(sender, args);
                break;
            case "stats":
                if (!hasAdmin(sender)) return true;
                stats(sender);
                break;
            case "verbose":
                if (!hasAdmin(sender)) return true;
                verbose(sender, args);
                break;
            default:
                send(sender, "§c未知子命令, 输入 /kac help 查看帮助");
                break;
        }
        return true;
    }

    // ---------- 子命令实现 ----------

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §cKeran Anti Cheat §8v" + plugin.getDescription().getVersion() + " =====\n" +
                        PREFIX + "§f/kac help §7- 帮助\n" +
                        PREFIX + "§f/kac alerts [on|off] §7- 切换告警接收\n" +
                        PREFIX + "§f/kac info <玩家> §7- 查看违规记录\n" +
                        PREFIX + "§f/kac reset <玩家> §7- 清零违规记录\n" +
                        PREFIX + "§f/kac checks §7- 查看检测器状态\n" +
                        PREFIX + "§f/kac toggle <检测器> [on|off] §7- 运行时开关检测器\n" +
                        PREFIX + "§f/kac stats §7- 全局违规统计\n" +
                        PREFIX + "§f/kac ban <玩家> [小时] [原因] §7- 封禁(0小时=永久)\n" +
                        PREFIX + "§f/kac unban <玩家> §7- 解封\n" +
                        PREFIX + "§f/kac kick <玩家> [原因] §7- 踢出\n" +
                        PREFIX + "§f/kac verbose [on|off] §7- 详细日志\n" +
                        PREFIX + "§f/kac reload §7- 重载配置"));
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "§c该命令仅限玩家使用");
            return;
        }
        Player p = (Player) sender;
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            data.alertsEnabled = true;
            send(sender, "§a告警通知已开启");
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            data.alertsEnabled = false;
            send(sender, "§a告警通知已关闭");
        } else {
            send(sender, "§f当前状态: " + (data.alertsEnabled ? "§a开启" : "§c关闭")
                    + " §7(用法: /kac alerts on|off)");
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac info <玩家>");
            return;
        }
        String name = args[1];
        Player online = Bukkit.getPlayerExact(name);
        PlayerData data;
        UUID uuid;
        if (online != null) {
            data = plugin.getCheckManager().getPlayerData(online);
            uuid = online.getUniqueId();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            uuid = op.getUniqueId();
            data = plugin.getCheckManager().loadPlayerData(uuid, op.getName() != null ? op.getName() : name);
        }
        if (data == null) {
            send(sender, "§c未找到玩家 " + name);
            return;
        }
        // 封禁状态
        String banInfo = plugin.getBanManager().getBanInfo(uuid);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c违规记录: §e" + data.getName() + " §8====="));
        if (banInfo != null) {
            sender.sendMessage(PREFIX + "§c封禁状态: §f" + banInfo);
        }
        boolean any = false;
        for (CheckType t : CheckType.values()) {
            int vl = data.getVl(t);
            int flags = data.getFlags(t);
            if (vl > 0 || flags > 0) {
                any = true;
                sender.sendMessage(PREFIX + "§f" + t.getDisplayName() + "§8(" + t.getId() + ") "
                        + "§cvl=" + vl + " §7违规次数=" + flags);
            }
        }
        if (!any) {
            send(sender, "§a无违规记录");
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac reset <玩家>");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            PlayerData data = plugin.getCheckManager().getPlayerData(online);
            if (data != null) {
                data.resetAllVl();
            }
            send(sender, "§a已清零 §e" + args[1] + " §a的违规记录");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
        // 从持久化文件清除
        java.io.File file = new java.io.File(plugin.getDataFolder(), "data" + java.io.File.separator + "vl.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            yml.set("players." + op.getUniqueId().toString(), null);
            try {
                yml.save(file);
            } catch (Exception ignored) {
            }
        }
        send(sender, "§a已清零 §e" + args[1] + " §a的违规记录");
    }

    private void checks(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c检测器状态 §8====="));
        for (Check c : plugin.getCheckManager().getChecks()) {
            boolean on = plugin.getCheckManager().isActive(c.getType());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "§8- §f" + c.getType().getDisplayName()
                            + "§8(" + c.getType().getId() + ") §7" + c.getType().getDescription()
                            + " " + (on ? "§a[开]" : "§c[关]")));
        }
    }

    private void toggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac toggle <检测器> [on|off] (检测器列表见 /kac checks)");
            return;
        }
        CheckType type = CheckType.fromId(args[1]);
        if (type == null) {
            send(sender, "§c未知检测器: " + args[1]);
            return;
        }
        boolean target;
        if (args.length >= 3) {
            target = args[2].equalsIgnoreCase("on");
        } else {
            target = plugin.getCheckManager().isDisabledByCommand(type);
        }
        plugin.getCheckManager().toggleByCommand(type, target);
        send(sender, "§a检测器 §e" + type.getDisplayName() + " §a已" + (target ? "启用" : "关闭") + " (重启后恢复配置)");
    }

    private void ban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac ban <玩家> [小时] [原因] (0小时=永久)");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        int hours = 0;
        int idx = 2;
        if (args.length >= 3) {
            try {
                hours = Integer.parseInt(args[2]);
                idx = 3;
            } catch (NumberFormatException ignored) {
            }
        }
        String reason = idx < args.length ? String.join(" ", Arrays.copyOfRange(args, idx, args.length))
                : "管理员封禁";
        plugin.getBanManager().ban(target, reason, hours);
        String msg = ChatColor.translateAlternateColorCodes('&',
                "§c[KeranAntiCheat] 你已被封禁\n§7" + plugin.getBanManager().getBanInfo(target.getUniqueId()));
        Bukkit.getScheduler().runTask(plugin, () -> target.kickPlayer(msg));
        send(sender, "§a已封禁 §e" + target.getName() + " §a(" + reason + ")");
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac unban <玩家>");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
        if (plugin.getBanManager().isBanned(op.getUniqueId())) {
            plugin.getBanManager().unban(op.getUniqueId());
            send(sender, "§a已解封 §e" + args[1]);
        } else {
            send(sender, "§e" + args[1] + " §7不在封禁列表中");
        }
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "§c用法: /kac kick <玩家> [原因]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "§c未找到在线玩家 " + args[1]);
            return;
        }
        String reason = args.length >= 3
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "管理员踢出";
        Bukkit.getScheduler().runTask(plugin, () -> target.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                "§c[KeranAntiCheat] 你被移出服务器\n§7原因: " + reason)));
        send(sender, "§a已踢出 §e" + target.getName());
    }

    private void stats(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c全局违规统计 §8====="));
        for (CheckType t : CheckType.values()) {
            int total = plugin.getCheckManager().getTotalFlags(t);
            if (total > 0) {
                sender.sendMessage(PREFIX + "§f" + t.getDisplayName()
                        + "§8(" + t.getId() + ") §c" + total + " §7次");
            }
        }
    }

    private void verbose(CommandSender sender, String[] args) {
        boolean on;
        if (args.length >= 2) {
            on = args[1].equalsIgnoreCase("on");
        } else {
            on = !plugin.getConfigManager().isVerbose();
        }
        plugin.getConfigManager().setVerbose(on);
        send(sender, "§a详细日志已" + (on ? "开启" : "关闭"));
    }

    // ---------- Tab 补全 ----------

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "reload", "alerts", "info", "reset", "checks", "toggle",
            "ban", "unban", "kick", "stats", "verbose");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "toggle":
                    for (String id : CheckType.allIds()) {
                        if (id.startsWith(args[1].toLowerCase())) {
                            result.add(id);
                        }
                    }
                    break;
                case "info":
                case "reset":
                case "ban":
                case "kick":
                case "unban":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            result.add(p.getName());
                        }
                    }
                    break;
                case "alerts":
                case "verbose":
                    result.addAll(Arrays.asList("on", "off"));
                    break;
                default:
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            result.addAll(Arrays.asList("on", "off"));
        }
        return result;
    }
}
