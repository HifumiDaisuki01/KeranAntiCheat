package com.keran.kac.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.keran.kac.KeranAntiCheat;
import com.keran.kac.bridge.ClientData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KeranClientBridge - 接收 KeranClient 客户端 mod 的指纹/心跳/详细清单, 检测作弊 mod。
 * 功能: 强制安装门槛 / 黑名单告警 / JVM 注入告警 / 心跳超时 / 管理员查询详细 mods 与材质包清单。
 */
public final class ClientBridgeModule implements PluginMessageListener, Listener {

    public static final String REPORT_CHANNEL = "kac:report";
    public static final String HEARTBEAT_CHANNEL = "kac:heartbeat";
    public static final String DETAIL_CHANNEL = "kac:detail";
    public static final String FILES_CHANNEL = "kac:files";
    public static final String QUERY_CHANNEL = "kac:query";

    private final KeranAntiCheat plugin;

    private final Map<UUID, ClientData> clientData = new ConcurrentHashMap<>();
    /** 查询等待表: 玩家UUID -> 查询上下文(请求者 + 请求类型) */
    private final Map<UUID, PendingQuery> pendingQueries = new ConcurrentHashMap<>();

    /** 查询上下文 */
    public static class PendingQuery {
        public final CommandSender requester;
        public final String type; // detail / modsmd5 / packsmd5

        public PendingQuery(CommandSender requester, String type) {
            this.requester = requester;
            this.type = type;
        }
    }

    public ClientBridgeModule(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    /** 由主插件 onEnable 调用 */
    public void init() {
        mergeMissingConfig();

        // 注册插件消息频道(收/发)
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, REPORT_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, HEARTBEAT_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, DETAIL_CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, FILES_CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, QUERY_CHANNEL);

        // 周期任务: 心跳超时检查 + 进服强制门槛
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkHeartbeats, 20L, 20L);

        // 玩家进服事件(强制安装门槛)
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("KeranClient 客户端桥接模块已启用 (强制安装 / 指纹上报 / MD5 严格校验)");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scheduleJoinCheck(e.getPlayer());
    }

    /** 由主插件 onDisable 调用 */
    public void shutdown() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        clientData.clear();
        pendingQueries.clear();
        channelRegistered.clear();
        joinTimes.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String json = decodeStringPayload(message);
        if (json == null || json.isEmpty()) {
            plugin.getLogger().warning("收到空上报数据来自 " + player.getName());
            return;
        }
        JsonObject obj;
        try {
            // 兼容服务器内置旧版 Gson(2.8.0 无 parseString)
            obj = new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("收到非法上报数据来自 " + player.getName() + ": " + e.getMessage());
            return;
        }

        switch (channel) {
            case REPORT_CHANNEL:
                handleReport(player, obj);
                break;
            case HEARTBEAT_CHANNEL:
                handleHeartbeat(player, obj);
                break;
            case DETAIL_CHANNEL:
                handleDetail(player, obj);
                break;
            case FILES_CHANNEL:
                handleFiles(player, obj);
                break;
            default:
                break;
        }
    }

    // ---------- 处理各类上报 ----------

    /** 处理完整指纹报告(进服自动上报) */
    private void handleReport(Player player, JsonObject obj) {
        UUID uuid = player.getUniqueId();
        ClientData data = clientData.computeIfAbsent(uuid, k -> new ClientData(player.getName()));

        data.name = player.getName();
        data.lastReport = System.currentTimeMillis();
        data.lastHeartbeat = System.currentTimeMillis();
        data.fingerprint = obj.has("fingerprint") ? obj.get("fingerprint").getAsString() : "";
        data.modCount = obj.has("mods") ? obj.getAsJsonArray("mods").size() : 0;
        data.blacklistHits = toStringList(obj, "blacklist_hits");
        data.blacklistSources = toStringList(obj, "blacklist_sources");
        data.suspicious = toStringList(obj, "suspicious");
        data.jvmAgents = toStringList(obj, "jvm_agents");
        data.suspiciousPacks = toStringList(obj, "suspicious_packs");

        // 全量文件清单(连入自动上报): mod_files / pack_files / 目录 MD5
        if (obj.has("mod_files") && obj.get("mod_files").isJsonArray()) {
            JsonObject files = new JsonObject();
            files.add("mod_files", obj.getAsJsonArray("mod_files"));
            JsonArray packsArr = obj.has("pack_files") && obj.get("pack_files").isJsonArray()
                    ? obj.getAsJsonArray("pack_files") : new JsonArray();
            files.add("pack_files", packsArr);
            data.filesReport = files;
            data.filesTime = System.currentTimeMillis();
            data.modFileCount = obj.getAsJsonArray("mod_files").size();
            data.packFileCount = packsArr.size();
        }
        if (obj.has("detail")) {
            // 兼容旧版 detail 字段
            data.detailReport = obj.getAsJsonObject("detail");
        } else {
            JsonObject detail = new JsonObject();
            detail.add("mods", obj.has("mods") ? obj.getAsJsonArray("mods") : new JsonArray());
            detail.add("mod_files", obj.has("mod_files") ? obj.getAsJsonArray("mod_files") : new JsonArray());
            detail.add("pack_files", obj.has("pack_files") ? obj.getAsJsonArray("pack_files") : new JsonArray());
            data.detailReport = detail;
        }
        data.detailTime = System.currentTimeMillis();
        data.modsDirMd5 = obj.has("mods_dir_md5") ? obj.get("mods_dir_md5").getAsString() : "";
        data.packsDirMd5 = obj.has("packs_dir_md5") ? obj.get("packs_dir_md5").getAsString() : "";

        // 后台/控制台显示两串目录 MD5(无论严格校验是否开启都显示)
        plugin.getLogger().info("[" + player.getName() + "] 收到 KeranClient 指纹报告: mods=" + data.modCount
                + " 黑名单=" + data.blacklistHits + " 可疑=" + data.suspicious
                + " mod文件=" + data.modFileCount + " 材质包文件=" + data.packFileCount
                + " | mods目录MD5=" + data.modsDirMd5 + " | 材质包目录MD5=" + data.packsDirMd5);

        // MD5 严格校验(默认关闭): 开启后客户端两串 MD5 必须与 config 完全一致, 否则踢出
        if (plugin.getConfigManager().get().getBoolean("strict-md5.enabled", false)) {
            boolean modsOk = true;
            String expectMods = plugin.getConfigManager().get().getString("strict-md5.mods-dir-md5", "");
            if (expectMods != null && !expectMods.isEmpty()) {
                modsOk = data.modsDirMd5 != null && data.modsDirMd5.equalsIgnoreCase(expectMods);
            }
            boolean packsOk = true;
            String expectPacks = plugin.getConfigManager().get().getString("strict-md5.packs-dir-md5", "");
            if (expectPacks != null && !expectPacks.isEmpty()) {
                packsOk = data.packsDirMd5 != null && data.packsDirMd5.equalsIgnoreCase(expectPacks);
            }
            if (!modsOk || !packsOk) {
                String msg = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().get().getString("strict-md5.kick-message",
                                "§c[KAC] §f客户端文件与服务器要求不一致, 已断开连接"));
                java.util.Map<String, String> ph = new java.util.HashMap<>();
                ph.put("player", player.getName());
                ph.put("mods_status", modsOk ? "一致" : "不一致");
                ph.put("packs_status", packsOk ? "一致" : "不一致");
                broadcastAlert(getMsg("md5-mismatch",
                        "§c[KAC] §e%player% §cMD5 与服务器要求不一致(mods=%mods_status% packs=%packs_status%), 已踢出", ph));
                plugin.getLogger().warning("玩家 " + player.getName() + " MD5 不匹配, 已踢出(严格校验): mods="
                        + data.modsDirMd5 + " 期望=" + expectMods + " packs=" + data.packsDirMd5 + " 期望=" + expectPacks);
                final String km = msg;
                Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(km));
                return;
            }
        }

        // 黑名单处理
        if (!data.blacklistHits.isEmpty()) {
            String hits = String.join(", ", data.blacklistHits);
            java.util.Map<String, String> ph = new java.util.HashMap<>();
            ph.put("player", player.getName());
            ph.put("details", hits);
            broadcastAlert(getMsg("blacklist-hit",
                    "§c[KAC] §e%player% §c检测到作弊模组: §f%details%", ph));
            plugin.getLogger().warning("玩家 " + player.getName() + " 命中作弊模组黑名单: " + hits
                    + " 来源=" + data.blacklistSources);
            if (plugin.getConfigManager().get().getBoolean("blacklist.kick", false)) {
                String msg = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().get().getString("blacklist.kick-message",
                                "§c[KAC] §f检测到作弊模组, 已断开连接"));
                Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(msg));
                return;
            }
        }
        // 可疑处理
        if (!data.suspicious.isEmpty() && plugin.getConfigManager().get().getBoolean("suspicious.alert", true)) {
            java.util.Map<String, String> ph = new java.util.HashMap<>();
            ph.put("player", player.getName());
            ph.put("details", String.join(", ", data.suspicious));
            broadcastAlert(getMsg("suspicious-hit",
                    "§e[KAC] §e%player% §7存在可疑模组/文件: §f%details%", ph));
        }
        // JVM 注入
        if (!data.jvmAgents.isEmpty()) {
            java.util.Map<String, String> ph = new java.util.HashMap<>();
            ph.put("player", player.getName());
            ph.put("details", String.join(", ", data.jvmAgents));
            broadcastAlert(getMsg("jvm-injection",
                    "§c[KAC] §e%player% §c检测到 JVM 注入: §f%details%", ph));
        }
        // 可疑资源包
        if (!data.suspiciousPacks.isEmpty()) {
            java.util.Map<String, String> ph = new java.util.HashMap<>();
            ph.put("player", player.getName());
            ph.put("details", String.join(", ", data.suspiciousPacks));
            broadcastAlert(getMsg("suspicious-pack",
                    "§e[KAC] §e%player% §7启用可疑资源包: §f%details%", ph));
        }
    }

    /** 处理心跳 */
    private void handleHeartbeat(Player player, JsonObject obj) {
        ClientData data = clientData.computeIfAbsent(player.getUniqueId(), k -> new ClientData(player.getName()));
        data.name = player.getName();
        data.lastHeartbeat = System.currentTimeMillis();
    }

    /** 处理详细清单回复 */
    private void handleDetail(Player player, JsonObject obj) {
        ClientData data = clientData.computeIfAbsent(player.getUniqueId(), k -> new ClientData(player.getName()));
        data.name = player.getName();
        data.detailReport = obj;
        data.detailTime = System.currentTimeMillis();
        plugin.getLogger().info("[" + player.getName() + "] 收到详细清单: mods=" + count(obj, "mods")
                + " mod文件=" + count(obj, "mod_files") + " 材质包文件=" + count(obj, "pack_files"));

        PendingQuery q = pendingQueries.remove(player.getUniqueId());
        if (q != null) {
            sendDetailTo(q.requester, player, data);
        }
    }

    /** 处理 MD5 清单回复 */
    private void handleFiles(Player player, JsonObject obj) {
        ClientData data = clientData.computeIfAbsent(player.getUniqueId(), k -> new ClientData(player.getName()));
        data.name = player.getName();
        data.filesReport = obj;
        data.filesTime = System.currentTimeMillis();
        plugin.getLogger().info("[" + player.getName() + "] 收到 MD5 清单: mod文件=" + count(obj, "mod_files")
                + " 材质包文件=" + count(obj, "pack_files"));

        PendingQuery q = pendingQueries.remove(player.getUniqueId());
        if (q != null) {
            if ("packsmd5".equals(q.type)) {
                sendPackMd5To(q.requester, player, data);
            } else {
                sendFilesTo(q.requester, player, data);
            }
        }
    }

    // ---------- 强制门槛 ----------

    /** 已登记频道注册的玩家(客户端注册了 kac: 频道 = 已装 mod) */
    private final java.util.Set<UUID> channelRegistered = ConcurrentHashMap.newKeySet();
    /** 玩家进服时刻(用于强制门槛超时计算) */
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    /**
     * 玩家进服: 立即开始重复检查, 未上报指纹/未注册频道则踢出。
     * 重复检查直到玩家上报或超时, 避免单次调度丢失。
     */
    public void scheduleJoinCheck(Player player) {
        if (!plugin.getConfigManager().get().getBoolean("join.enabled", true)) {
            return;
        }
        if (player.hasPermission("kacc.bypass")) {
            return;
        }
        int wait = Math.max(2, plugin.getConfigManager().get().getInt("join.wait-seconds", 5));
        UUID uuid = player.getUniqueId();
        joinTimes.put(uuid, System.currentTimeMillis());
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            ClientData d = clientData.get(uuid);
            boolean reported = d != null && d.hasReported();
            boolean hasChannel = channelRegistered.contains(uuid);
            if (reported || hasChannel) {
                task.cancel();
                return;
            }
            // 进服超过 wait 秒仍未上报 → 踢
            Long joinMs = joinTimes.get(uuid);
            if (joinMs != null && System.currentTimeMillis() - joinMs >= wait * 1000L) {
                task.cancel();
                joinTimes.remove(uuid);
                boolean kick = plugin.getConfigManager().get().getBoolean("join.kick-if-no-client", true);
                if (kick) {
                    String msg = ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().get().getString("join.kick-message",
                                    "§c[KAC] §f本服务器要求安装 KeranClient 反作弊客户端"));
                    player.kickPlayer(msg);
                    plugin.getLogger().warning("玩家 " + player.getName() + " 未安装 KeranClient, 已踢出(强制安装)");
                } else if (plugin.getConfigManager().get().getBoolean("join.alert", true)) {
                    broadcastAlert(getMsg("join-no-client",
                            "§e[KAC] §e%player% §7未安装 KeranClient 客户端", player.getName()));
                }
            }
        }, 10L, 20L); // 0.5 秒后开始, 每 1 秒检查一次
    }

    /** 监听客户端频道注册: 客户端注册 kac:report = 已装 KeranClient mod */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onChannelRegister(org.bukkit.event.player.PlayerRegisterChannelEvent e) {
        String channel = e.getChannel();
        if (channel != null && channel.startsWith("kac:")) {
            channelRegistered.add(e.getPlayer().getUniqueId());
            plugin.getLogger().info("[" + e.getPlayer().getName() + "] 客户端已注册频道 " + channel + " (识别为已安装 KeranClient)");
        }
    }

    /** 玩家离开时清理登记 */
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        channelRegistered.remove(e.getPlayer().getUniqueId());
        joinTimes.remove(e.getPlayer().getUniqueId());
        pendingQueries.remove(e.getPlayer().getUniqueId());
        clientData.remove(e.getPlayer().getUniqueId());
    }

    // ---------- 管理员查询 ----------

    /** 请求客户端详细清单 */
    public boolean requestDetail(Player target, CommandSender requester) {
        return sendQuery(target, requester, "detail");
    }

    /** 请求客户端 MD5 清单(mods 目录) */
    public boolean requestModsMd5(Player target, CommandSender requester) {
        return sendQuery(target, requester, "modsmd5");
    }

    /** 请求客户端 MD5 清单(材质包目录) */
    public boolean requestPacksMd5(Player target, CommandSender requester) {
        return sendQuery(target, requester, "packsmd5");
    }

    private boolean sendQuery(Player target, CommandSender requester, String type) {
        if (target == null || !target.isOnline()) {
            return false;
        }
        ClientData d = clientData.get(target.getUniqueId());
        if (d == null || !d.hasReported()) {
            return false;
        }
        // 负载: 命令类型字符串(客户端 QueryReceiver 读取)
        byte[] msg = type.getBytes(StandardCharsets.UTF_8);
        target.sendPluginMessage(plugin, QUERY_CHANNEL, msg);
        pendingQueries.put(target.getUniqueId(), new PendingQuery(requester, type));
        // 8 秒超时清理
        UUID targetId = target.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingQueries.remove(targetId) != null) {
                requester.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "§8[§cKACClient§8] §e" + target.getName() + " §7未回复查询(可能客户端掉线)"));
            }
        }, 160L);
        return true;
    }

    // ---------- 输出格式化 ----------

    public void sendDetailTo(CommandSender to, Player player, ClientData d) {
        if (d.detailReport == null) {
            to.sendMessage(PREFIX + "§7暂无详细清单, 请稍后重试");
            return;
        }
        JsonObject o = d.detailReport;
        to.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c客户端详细清单: §e" + player.getName() + " §8====="));
        to.sendMessage(PREFIX + "§f已加载 mod: §e" + count(o, "mods") + " §7个");
        JsonArray mods = o.has("mods") ? o.getAsJsonArray("mods") : new JsonArray();
        for (int i = 0; i < mods.size(); i++) {
            JsonObject m = mods.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(m, "id") + " §7v" + safe(m, "version") + " §8(" + safe(m, "name") + ")");
        }
        to.sendMessage(PREFIX + "§fmods 目录文件: §e" + count(o, "mod_files") + " §7个");
        JsonArray modFiles = o.has("mod_files") ? o.getAsJsonArray("mod_files") : new JsonArray();
        for (int i = 0; i < modFiles.size(); i++) {
            JsonObject m = modFiles.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(m, "file") + " §8" + formatSize(m.has("size") ? m.get("size").getAsLong() : 0));
        }
        to.sendMessage(PREFIX + "§f材质包文件: §e" + count(o, "pack_files") + " §7个");
        JsonArray packs = o.has("pack_files") ? o.getAsJsonArray("pack_files") : new JsonArray();
        for (int i = 0; i < packs.size(); i++) {
            JsonObject p = packs.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(p, "file") + " §8" + formatSize(p.has("size") ? p.get("size").getAsLong() : 0));
        }
    }

    public void sendFilesTo(CommandSender to, Player player, ClientData d) {
        if (d.filesReport == null) {
            to.sendMessage(PREFIX + "§7暂无 MD5 清单(客户端未上报或版本过旧), 请稍后重试");
            return;
        }
        JsonObject o = d.filesReport;
        to.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §cMD5 校验清单: §e" + player.getName() + " §8====="));
        if (d.modsDirMd5 != null && !d.modsDirMd5.isEmpty()) {
            to.sendMessage(PREFIX + "§fmods 目录整体 MD5: §e" + d.modsDirMd5);
        }
        if (d.packsDirMd5 != null && !d.packsDirMd5.isEmpty()) {
            to.sendMessage(PREFIX + "§f材质包目录整体 MD5: §e" + d.packsDirMd5);
        }
        to.sendMessage(PREFIX + "§fmods 目录: §e" + count(o, "mod_files") + " §7个文件");
        JsonArray modFiles = o.has("mod_files") ? o.getAsJsonArray("mod_files") : new JsonArray();
        for (int i = 0; i < modFiles.size(); i++) {
            JsonObject m = modFiles.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(m, "file") + " §8" + formatSize(m.has("size") ? m.get("size").getAsLong() : 0)
                    + "\n§8     md5: §7" + safe(m, "md5"));
        }
        to.sendMessage(PREFIX + "§f材质包目录: §e" + count(o, "pack_files") + " §7个文件");
        JsonArray packs = o.has("pack_files") ? o.getAsJsonArray("pack_files") : new JsonArray();
        for (int i = 0; i < packs.size(); i++) {
            JsonObject p = packs.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(p, "file") + " §8" + formatSize(p.has("size") ? p.get("size").getAsLong() : 0)
                    + "\n§8     md5: §7" + safe(p, "md5"));
        }
    }

    /** 仅显示材质包目录 MD5 */
    public void sendPackMd5To(CommandSender to, Player player, ClientData d) {
        if (d.filesReport == null) {
            to.sendMessage(PREFIX + "§7暂无 MD5 清单(客户端未上报或版本过旧), 请稍后重试");
            return;
        }
        JsonObject o = d.filesReport;
        to.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "§8===== §c材质包目录 MD5: §e" + player.getName() + " §8====="));
        if (d.packsDirMd5 != null && !d.packsDirMd5.isEmpty()) {
            to.sendMessage(PREFIX + "§f材质包目录整体 MD5: §e" + d.packsDirMd5);
        }
        JsonArray packs = o.has("pack_files") ? o.getAsJsonArray("pack_files") : new JsonArray();
        if (packs.size() == 0) {
            to.sendMessage(PREFIX + "§7(材质包目录为空)");
            return;
        }
        for (int i = 0; i < packs.size(); i++) {
            JsonObject p = packs.get(i).getAsJsonObject();
            to.sendMessage(PREFIX + " §7- §f" + safe(p, "file") + " §8" + formatSize(p.has("size") ? p.get("size").getAsLong() : 0)
                    + "\n§8     md5: §7" + safe(p, "md5"));
        }
    }

    // ---------- 心跳检查 ----------

    private void checkHeartbeats() {
        if (!plugin.getConfigManager().get().getBoolean("heartbeat.enabled", true)) {
            return;
        }
        long timeout = plugin.getConfigManager().get().getLong("heartbeat.timeout-seconds", 90) * 1000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ClientData> e : clientData.entrySet()) {
            ClientData d = e.getValue();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                continue;
            }
            if (p.hasPermission("kac.bypass")) {
                continue;
            }
            if (d.lastReport > 0 && d.lastHeartbeat > 0 && now - d.lastHeartbeat > timeout) {
                d.heartbeatTimeoutWarned++;
                if (d.heartbeatTimeoutWarned <= 3 && plugin.getConfigManager().get().getBoolean("heartbeat.alert", true)) {
                    java.util.Map<String, String> ph = new java.util.HashMap<>();
                    ph.put("player", p.getName());
                    ph.put("seconds", String.valueOf(plugin.getConfigManager().get().getLong("heartbeat.timeout-seconds", 90)));
                    broadcastAlert(getMsg("heartbeat-lost",
                            "§e[KAC] §e%player% §7客户端检测已断开(疑似卸载) 超过 %seconds% 秒", ph));
                }
            }
        }
    }

    // ---------- 工具 ----------

    public Map<UUID, ClientData> getClientData() {
        return clientData;
    }

    public ClientData getClientData(Player p) {
        return clientData.get(p.getUniqueId());
    }

    private int count(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key).size() : 0;
    }

    private String safe(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "?";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.2f MB", bytes / 1048576.0);
    }

    /** 解析 VarInt 前缀字符串 (兼容 CraftBukkit 传递的原始 payload) */
    private String decodeStringPayload(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            int pos = 0;
            int length = 0;
            int shift = 0;
            while (pos < data.length) {
                byte b = data[pos++];
                length |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 35) {
                    return null;
                }
            }
            if (pos + length > data.length) {
                // 容错: 有些平台直接传原始 UTF-8
                return new String(data, StandardCharsets.UTF_8);
            }
            return new String(data, pos, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<String> toStringList(JsonObject obj, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (int i = 0; i < arr.size(); i++) {
                list.add(arr.get(i).getAsString());
            }
        }
        return list;
    }

    public static final String PREFIX = "§8[§cKAC§8] §7";

    /**
     * 配置自动补全: 把 jar 内默认 config.yml 中、当前磁盘 config.yml 缺失的节点补进去。
     * 解决旧版插件升级后新配置项(如 messages 段)不自动出现的问题。
     */
    private void mergeMissingConfig() {
        try {
            java.io.InputStream defStream = plugin.getResource("config.yml");
            if (defStream == null) {
                return;
            }
            org.bukkit.configuration.file.YamlConfiguration def =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(defStream, StandardCharsets.UTF_8));
            java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
            org.bukkit.configuration.file.YamlConfiguration current =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            boolean changed = false;
            for (String key : def.getKeys(true)) {
                if (!current.contains(key)) {
                    current.set(key, def.get(key));
                    changed = true;
                }
            }
            if (changed) {
                current.save(file);
                plugin.getLogger().info("检测到旧版配置, 已自动补全缺失配置项");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("配置自动补全失败: " + e.getMessage());
        }
    }

    /** 读取可自定义提示性文本(messages 段), 替换占位符, 处理颜色代码。
     * 占位符: %player% %details% %seconds% %mods_status% %packs_status%
     */
    public String getMsg(String key, String def, java.util.Map<String, String> placeholders) {
        String msg = plugin.getConfigManager().get().getString("messages-bridge." + key, def);
        if (msg == null) {
            msg = def;
        }
        if (placeholders != null) {
            for (java.util.Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("%" + e.getKey() + "%", e.getValue() == null ? "?" : e.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /** 便捷: 纯玩家名替换 */
    public String getMsg(String key, String def, String playerName) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("player", playerName);
        return getMsg(key, def, m);
    }

    private void broadcastAlert(String msg) {
        String colored = ChatColor.translateAlternateColorCodes('&', msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("kac.notify")) {
                p.sendMessage(colored);
            }
        }
        plugin.getLogger().warning(ChatColor.stripColor(colored));
    }
}
