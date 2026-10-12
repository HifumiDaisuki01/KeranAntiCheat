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
    private final Map<UUID, String> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> messageWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> messageCounts = new ConcurrentHashMap<>();

    private final Map<UUID, ClientData> clientData = new ConcurrentHashMap<>();
    /** 查询等待表: 玩家UUID -> 查询上下文(请求者 + 请求类型) */
    private final Map<UUID, PendingQuery> pendingQueries = new ConcurrentHashMap<>();

    /** 查询上下文 */
    public static class PendingQuery {
        public final CommandSender requester;
        public final String type; // detail / modsmd5 / packsmd5
        public final String requestId = UUID.randomUUID().toString();

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
        for (Player p : Bukkit.getOnlinePlayers()) scheduleJoinCheck(p);

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
        sessions.clear();
        messageWindows.clear();
        messageCounts.clear();
        joinTimes.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - messageWindows.getOrDefault(id, 0L) >= 1000) {
            messageWindows.put(id, now); messageCounts.put(id, 0);
        }
        if (message == null || message.length > 32767 || messageCounts.merge(id, 1, Integer::sum) > 8) return;
        String json = MessageCodec.decode(message);
        if (json == null || json.isEmpty() || !MessageCodec.reasonableDepth(json)) {
            if (messageCounts.getOrDefault(id, 0) == 1) plugin.getLogger().warning("拒绝非法客户端帧: " + player.getName());
            return;
        }
        JsonObject obj;
        try {
            // 兼容服务器内置旧版 Gson(2.8.0 无 parseString)
            obj = new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            if (messageCounts.getOrDefault(id, 0) == 1) plugin.getLogger().warning("拒绝非法客户端JSON: " + player.getName());
            return;
        }

        try {
        validateEnvelope(player, obj);
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
        } catch (RuntimeException error) {
            // At most one parse/validation error per player per second.
            if (messageCounts.getOrDefault(id, 0) == 1)
                plugin.getLogger().warning("拒绝 " + player.getName() + " 的客户端消息: " + error.getMessage());
        }
    }

    private void validateEnvelope(Player player, JsonObject obj) {
        if (!player.getName().equalsIgnoreCase(safe(obj, "player"))
                || !(plugin.getDescription().getVersion().equals(safe(obj, "v")) || "2.1.0".equals(safe(obj, "v")))
                || !java.util.Objects.equals(sessions.get(player.getUniqueId()), safe(obj, "nonce")))
            throw new IllegalArgumentException("客户端版本或会话不匹配，请升级配套 KeranClient");
        if (!obj.has("ts") || !obj.get("ts").isJsonPrimitive()) throw new IllegalArgumentException("缺少时间戳");
        long ts = obj.get("ts").getAsLong();
        long now = System.currentTimeMillis();
        if (ts < now - 600000 || ts > now + 600000) throw new IllegalArgumentException("时间戳超出窗口");
        for (Map.Entry<String, com.google.gson.JsonElement> field : obj.entrySet()) {
            if (field.getValue().isJsonArray()) {
                if (field.getValue().getAsJsonArray().size() > 512) throw new IllegalArgumentException("数组过大");
            } else if (field.getValue().isJsonPrimitive() && field.getValue().getAsString().length() > 1024)
                throw new IllegalArgumentException("字段过长");
        }
    }

    // ---------- 处理各类上报 ----------

    /**
     * 校验上报内容是否"像"真实 KeranClient 生成。
     *
     * <p><b>修复 KAC-02</b>：此前 {@code handleReport()} 只判断 JSON 能否解析，
     * 作弊者注入 3 字节 {@code {}} 即可让 {@code hasReported()} 变 true，
     * 完全绕过"强制安装"门槛。此处加入字段完整性与自洽性校验。
     *
     * <p>校验项：
     * <ol>
     *   <li>{@code player} 字段必须与登录名一致（防跨玩家重放）</li>
     *   <li>{@code v} 字段必须存在且为已知版本前缀</li>
     *   <li>{@code mods} 必须是数组且非空（真实客户端至少含自身）</li>
     *   <li>{@code fingerprint} 必须为 64 位十六进制（与客户端 SHA-256 一致）</li>
     *   <li>{@code fingerprint} 必须与上报的 mods 列表自洽（服务端复算比对）</li>
     *   <li>{@code ts} 时间戳必须存在且在合理窗口内（防重放旧包）</li>
     * </ol>
     *
     * @return null 表示校验通过；否则返回失败原因（用于日志/告警）
     */
    private String validateReport(Player player, JsonObject obj) {
        // 1. player 字段一致性
        if (!obj.has("player") || !obj.get("player").isJsonPrimitive()) {
            return "缺少 player 字段";
        }
        String claimed = obj.get("player").getAsString();
        if (claimed == null || !claimed.equalsIgnoreCase(player.getName())) {
            return "player 字段与登录名不符(" + claimed + " != " + player.getName() + ")";
        }

        // 2. 版本字段
        if (!obj.has("v") || !obj.get("v").isJsonPrimitive()) {
            return "缺少版本字段 v";
        }
        String ver = obj.get("v").getAsString();
        if (ver == null || ver.isEmpty() || ver.length() > 16) {
            return "版本字段异常(" + ver + ")";
        }

        // 3. mods 必须是非空数组
        if (!obj.has("mods") || !obj.get("mods").isJsonArray()) {
            return "缺少 mods 数组";
        }
        JsonArray mods = obj.getAsJsonArray("mods");
        if (mods.size() == 0) {
            return "mods 列表为空(客户端至少应包含自身)";
        }
        // 数组元素必须是对象且含 id 字段
        for (int i = 0; i < mods.size(); i++) {
            if (!mods.get(i).isJsonObject()) {
                return "mods[" + i + "] 不是对象";
            }
            JsonObject m = mods.get(i).getAsJsonObject();
            if (!m.has("id") || !m.get("id").isJsonPrimitive()) {
                return "mods[" + i + "] 缺少 id";
            }
        }

        boolean hasClient = false;
        for (com.google.gson.JsonElement element : mods) {
            JsonObject m = element.getAsJsonObject();
            if ("keranclient".equals(safe(m, "id"))) hasClient = true;
            if (!m.has("version") || !m.get("version").isJsonPrimitive()) return "缺少 mod version";
        }
        if (!hasClient) return "报告缺少 keranclient";

        // 4. fingerprint 必须为 64 位十六进制
        if (!obj.has("fingerprint") || !obj.get("fingerprint").isJsonPrimitive()) {
            return "缺少 fingerprint";
        }
        String fp = obj.get("fingerprint").getAsString();
        if (fp == null || !fp.matches("^[0-9a-fA-F]{64}$")) {
            return "fingerprint 格式非法(应为 64 位十六进制)";
        }

        // 5. fingerprint 自洽性: 与客户端 computeFingerprint 同算法复算
        String recomputed = recomputeFingerprint(mods);
        if (!fp.equalsIgnoreCase(recomputed)) {
            return "fingerprint 与 mods 列表不自洽(上报=" + fp.substring(0, 12)
                    + "... 复算=" + recomputed.substring(0, 12) + "...)";
        }

        // 6. 时间戳窗口(10 分钟, 防重放)
        if (obj.has("ts") && obj.get("ts").isJsonPrimitive()) {
            try {
                long ts = obj.get("ts").getAsLong();
                long skew = Math.abs(System.currentTimeMillis() - ts);
                if (skew > 10 * 60 * 1000L) {
                    return "时间戳偏差过大(" + (skew / 1000) + "s)";
                }
            } catch (Exception ignored) {
                // ts 非数字 → 容忍, 不影响主校验
            }
        }

        return null;
    }

    /**
     * 服务端复算指纹, 与客户端 {@code ClientReport.computeFingerprint()} 保持同算法:
     * 取各 mod 的 {@code id@version}, 排序后用 {@code ;} 连接, 取 SHA-256 小写十六进制。
     */
    private String recomputeFingerprint(JsonArray mods) {
        try {
            java.util.List<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < mods.size(); i++) {
                JsonObject o = mods.get(i).getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "?";
                String ver = o.has("version") ? o.get("version").getAsString() : "?";
                list.add(id + "@" + ver);
            }
            java.util.Collections.sort(list);
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s).append(';');
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 处理完整指纹报告(进服自动上报) */
    private void handleReport(Player player, JsonObject obj) {
        UUID uuid = player.getUniqueId();
        ClientData data = clientData.computeIfAbsent(uuid, k -> new ClientData(player.getName()));

        // ===== 修复 KAC-02: 上报内容校验(失败则视为"未上报", 不进门槛白名单) =====
        String invalidReason = validateReport(player, obj);
        if (invalidReason != null) {
            data.invalidReports++;
            // 连续 2 次非法上报才告警, 避免误伤协议升级期的旧客户端
            if (data.invalidReports == 2) {
                java.util.Map<String, String> ph = new java.util.HashMap<>();
                ph.put("player", player.getName());
                ph.put("details", invalidReason);
                broadcastAlert(getMsg("invalid-report",
                        "§c[KAC] §e%player% §c上报的客户端指纹无效: §f%details%", ph));
            }
            plugin.getLogger().warning("玩家 " + player.getName()
                    + " 上报无效指纹(第 " + data.invalidReports + " 次): " + invalidReason);
            return;
        }
        data.invalidReports = 0;

        data.name = player.getName();
        data.lastReport = 0;
        data.fingerprint = obj.has("fingerprint") ? obj.get("fingerprint").getAsString() : "";
        data.modCount = obj.has("mods") ? obj.getAsJsonArray("mods").size() : 0;
        data.blacklistHits = toStringList(obj, "blacklist_hits");
        // Apply server-maintained policy as well as client hints.
        for (String blocked : plugin.getConfigManager().get().getStringList("blacklist.mod-ids")) {
            for (com.google.gson.JsonElement mod : obj.getAsJsonArray("mods")) {
                if (blocked.equalsIgnoreCase(safe(mod.getAsJsonObject(), "id")) && !data.blacklistHits.contains(blocked))
                    data.blacklistHits.add(blocked);
            }
        }
        data.blacklistSources = toStringList(obj, "blacklist_sources");
        data.suspicious = toStringList(obj, "suspicious");
        data.jvmAgents = toStringList(obj, "jvm_agents");
        data.suspiciousPacks = toStringList(obj, "suspicious_packs");

        data.modFileCount = obj.has("mod_file_count") ? Math.max(0, Math.min(512, obj.get("mod_file_count").getAsInt())) : 0;
        data.packFileCount = obj.has("pack_file_count") ? Math.max(0, Math.min(512, obj.get("pack_file_count").getAsInt())) : 0;

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
        data.lastReport = System.currentTimeMillis();
        data.lastHeartbeat = data.lastReport;
        data.heartbeatTimeoutWarned = 0;
    }

    /** 处理心跳 */
    private void handleHeartbeat(Player player, JsonObject obj) {
        ClientData data = clientData.computeIfAbsent(player.getUniqueId(), k -> new ClientData(player.getName()));
        data.name = player.getName();
        if (!data.hasReported()) return;
        data.lastHeartbeat = System.currentTimeMillis();
        data.heartbeatTimeoutWarned = 0;
    }

    /** 处理详细清单回复 */
    private void handleDetail(Player player, JsonObject obj) {
        ClientData data = clientData.computeIfAbsent(player.getUniqueId(), k -> new ClientData(player.getName()));
        data.name = player.getName();
        data.detailReport = obj;
        data.detailTime = System.currentTimeMillis();
        plugin.getLogger().info("[" + player.getName() + "] 收到详细清单: mods=" + count(obj, "mods")
                + " mod文件=" + count(obj, "mod_files") + " 材质包文件=" + count(obj, "pack_files"));

        PendingQuery q = pendingQueries.get(player.getUniqueId());
        if (q != null && "detail".equals(q.type) && q.requestId.equals(safe(obj, "request_id"))
                && pendingQueries.remove(player.getUniqueId(), q)) {
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

        PendingQuery q = pendingQueries.get(player.getUniqueId());
        if (q != null && !"detail".equals(q.type) && q.requestId.equals(safe(obj, "request_id"))
                && pendingQueries.remove(player.getUniqueId(), q)) {
            if ("packsmd5".equals(q.type)) {
                sendPackMd5To(q.requester, player, data);
            } else {
                sendFilesTo(q.requester, player, data);
            }
        }
    }

    // ---------- 强制门槛 ----------

    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public boolean canPlay(Player player) {
        ClientData data = clientData.get(player.getUniqueId());
        return ClientPolicy.canPlay(plugin.getConfigManager().isStrictClientMode(), bypassClient(player),
                data != null && data.hasReported());
    }

    private boolean bypassClient(Player p) {
        return p.hasPermission("kac.bypass") || p.hasPermission("kacc.bypass");
    }

    public void scheduleJoinCheck(Player player) {
        UUID uuid = player.getUniqueId();
        sessions.put(uuid, UUID.randomUUID().toString());
        clientData.remove(uuid);
        joinTimes.put(uuid, System.currentTimeMillis());
    }

    /** Called by /kac reload: only the current strict-mode setting controls admission. */
    public void reloadPolicy() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ClientData d = clientData.get(p.getUniqueId());
            if (d == null || !d.hasReported()) joinTimes.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onChannelRegister(org.bukkit.event.player.PlayerRegisterChannelEvent e) {
        if (e.getChannel().equals(QUERY_CHANNEL))
            Bukkit.getScheduler().runTask(plugin, () -> sendChallenge(e.getPlayer()));
    }

    private void sendChallenge(Player p) {
        String nonce = sessions.get(p.getUniqueId());
        if (nonce != null && p.isOnline() && p.getListeningPluginChannels().contains(QUERY_CHANNEL))
            sendString(p, "challenge:" + nonce);
    }

    private void checkAdmissions() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ClientData d = clientData.get(id);
            if (d != null && d.hasReported()) { joinTimes.remove(id); continue; }
            if (!sessions.containsKey(id)) scheduleJoinCheck(p);
            Long start = joinTimes.get(id);
            if (start == null) continue;
            sendChallenge(p);
            if (now - start >= plugin.getConfigManager().getClientWaitSeconds() * 1000L) {
                if (plugin.getConfigManager().isStrictClientMode() && !bypassClient(p)) {
                    p.kickPlayer(ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().get().getString(
                            "join.kick-message", "§c[KAC] 请安装配套 KeranClient 后重新加入")));
                }
                joinTimes.remove(id);
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sessions.remove(id); joinTimes.remove(id); pendingQueries.remove(id); clientData.remove(id);
        messageWindows.remove(id); messageCounts.remove(id);
    }

    private void sendString(Player target, String text) {
        target.sendPluginMessage(plugin, QUERY_CHANNEL, MessageCodec.encode(text));
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
        if (pendingQueries.containsKey(target.getUniqueId())) return false;
        PendingQuery query = new PendingQuery(requester, type);
        pendingQueries.put(target.getUniqueId(), query);
        sendString(target, type + ":" + query.requestId);
        // 8 秒超时清理
        UUID targetId = target.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingQueries.remove(targetId, query)) {
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
        checkAdmissions();
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
            if (bypassClient(p)) {
                continue;
            }
            if (d.lastReport > 0 && d.lastHeartbeat > 0 && now - d.lastHeartbeat > Math.max(10000, timeout)) {
                if (plugin.getConfigManager().isStrictClientMode()) {
                    p.kickPlayer("§c[KAC] 客户端心跳超时，请检查连接后重新加入");
                    continue;
                }
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
