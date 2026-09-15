package com.keran.kac;

import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.check.combat.*;
import com.keran.kac.check.movement.*;
import com.keran.kac.check.world.*;
import com.keran.kac.config.ConfigManager;
import com.keran.kac.data.BanManager;
import com.keran.kac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 检测器注册中心、玩家数据仓库与违规处理入口。
 *
 * <p>v3.0 变更：违规值改为 double 权重制，并加入时间衰减（VL 会随时间自然回落），
 * 避免偶发误报长期累积后触发踢出/封禁。
 */
public class CheckManager {

    private final KeranAntiCheat plugin;
    private final List<Check> checks = new ArrayList<>();
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Set<CheckType> disabledByCommand = EnumSet.noneOf(CheckType.class);
    private final Map<CheckType, Integer> totalFlags = new EnumMap<>(CheckType.class);
    private long tick = 0;

    public CheckManager(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    /** 注册所有检测器 */
    public void registerChecks() {
        // ===== 移动检测 =====
        register(new FlyCheck(plugin));
        register(new SpeedCheck(plugin));
        register(new TimerCheck(plugin));
        register(new NoFallCheck(plugin));
        register(new JesusCheck(plugin));
        register(new SpiderCheck(plugin));
        register(new BlinkCheck(plugin));
        register(new ElytraCheck(plugin));
        register(new NoSlowCheck(plugin));
        register(new StepCheck(plugin));
        register(new GroundSpoofCheck(plugin));
        // ===== 战斗检测 =====
        register(new KillAuraCheck(plugin));
        register(new AimCheck(plugin));
        register(new ReachCheck(plugin));
        register(new CriticalsCheck(plugin));
        register(new AutoClickerCheck(plugin));
        register(new VelocityCheck(plugin));
        register(new AntiKbCheck(plugin));
        register(new HitboxCheck(plugin));
        register(new BacktrackCheck(plugin));
        register(new AutoBlockCheck(plugin));
        // ===== 世界/交互检测 =====
        register(new XRayCheck(plugin));
        register(new NukerCheck(plugin));
        register(new ScaffoldCheck(plugin));
        register(new FastUseCheck(plugin));
        register(new FastBreakCheck(plugin));
        register(new ChatCheck(plugin));
        register(new InventoryCheck(plugin));

        plugin.getLogger().info("已注册 " + checks.size() + " 个检测器");
    }

    private void register(Check check) {
        checks.add(check);
    }

    public List<Check> getChecks() {
        return checks;
    }

    public Check getCheck(CheckType type) {
        for (Check c : checks) {
            if (c.getType() == type) {
                return c;
            }
        }
        return null;
    }

    // ---------- PlayerData 管理 ----------

    public PlayerData getPlayerData(Player p) {
        return playerData.get(p.getUniqueId());
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    public void addPlayer(Player p) {
        playerData.put(p.getUniqueId(), new PlayerData(p));
    }

    public void removePlayer(Player p) {
        PlayerData d = playerData.remove(p.getUniqueId());
        if (d != null) {
            savePlayerData(p.getUniqueId(), d);
        }
    }

    /** 保存单个玩家数据到 data/vl.yml (合并写入) */
    public void savePlayerData(UUID uuid, PlayerData d) {
        File file = new File(plugin.getDataFolder(), "data" + File.separator + "vl.yml");
        try {
            YamlConfiguration yml = file.exists()
                    ? YamlConfiguration.loadConfiguration(file)
                    : new YamlConfiguration();
            d.serialize(yml.createSection("players." + uuid.toString()));
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存玩家 " + d.getName() + " 数据失败: " + e.getMessage());
        }
    }

    public Collection<PlayerData> getAllData() {
        return playerData.values();
    }

    // ---------- 事件路由 ----------

    /** 玩家是否被豁免该检测(权限豁免 + 配置豁免) */
    public boolean isExempt(Player p, CheckType type) {
        if (p == null || !p.isOnline()) {
            return false;
        }
        if (p.hasPermission("kac.bypass")) {
            return true;
        }
        if (p.hasPermission("kac.bypass." + type.getId())) {
            return true;
        }
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.isBypassAdmin() && p.hasPermission("kac.admin")) {
            return true;
        }
        if (cfg.isBypassOp() && p.isOp()) {
            return true;
        }
        return false;
    }

    /** 检测是否处于激活状态 */
    public boolean isActive(CheckType type) {
        ConfigManager cfg = plugin.getConfigManager();
        return cfg.isCheckEnabled(type) && !disabledByCommand.contains(type);
    }

    public boolean isDisabledByCommand(CheckType type) {
        return disabledByCommand.contains(type);
    }

    public void toggleByCommand(CheckType type, boolean on) {
        if (on) {
            disabledByCommand.remove(type);
        } else {
            disabledByCommand.add(type);
        }
    }

    // ---------- 违规处理 ----------

    /** 兼容旧签名: 权重 1.0 */
    public void handleFlag(PlayerData data, CheckType type, String info, int addVl) {
        handleFlag(data, type, info, (double) addVl);
    }

    /**
     * 处理一次违规：累加加权 vl、冷却、告警、处罚。
     * 若在异步线程被调用(如聊天事件), 自动切到主线程执行以保证安全。
     *
     * @param weight 违规权重（0.1~3.0，越大越严重）
     */
    public void handleFlag(PlayerData data, CheckType type, String info, double weight) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleFlagSync(data, type, info, weight));
            return;
        }
        handleFlagSync(data, type, info, weight);
    }

    private void handleFlagSync(PlayerData data, CheckType type, String info, double weight) {
        Player player = Bukkit.getPlayer(data.getUuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!isActive(type)) {
            return;
        }
        if (isExempt(player, type)) {
            return;
        }

        data.addVl(type, weight);
        data.addFlag(type);
        totalFlags.merge(type, 1, Integer::sum);

        double vl = data.getVl(type);
        ConfigManager cfg = plugin.getConfigManager();
        long now = System.currentTimeMillis();

        // 告警冷却
        boolean notify = now - data.getLastNotify(type) >= cfg.getNotifyCooldownSeconds() * 1000L;
        if (notify) {
            data.setLastNotify(type, now);
        }

        // 控制台日志(所有违规都记录)
        plugin.getLogger().warning(String.format(
                "[KAC-FLAG] %s 触发 %s(%s) vl=%.1f w=%.1f info=%s loc=%s",
                data.getName(), type.getDisplayName(), type.getId(), vl, weight, info,
                formatLoc(player)));

        if (notify) {
            broadcastAlert(data, type, vl, info,
                    player.getLocation().getWorld() == null ? "?" : player.getLocation().getWorld().getName());
        }

        // 处罚阶梯
        int kickVl = cfg.getKickVl(type);
        int banVl = cfg.getBanVl(type);
        if (banVl > 0 && vl >= banVl) {
            data.resetVl(type);
            data.addPunishment(type);
            plugin.getBanManager().ban(player, type.getDisplayName(), 0);
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    cfg.getBanMessage().replace("%reason%", type.getDisplayName() + " (vl=" + trim(vl) + ")")));
        } else if (kickVl > 0 && vl >= kickVl) {
            data.resetVl(type);
            data.addPunishment(type);
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    cfg.getKickMessage().replace("%reason%", type.getDisplayName() + " (vl=" + trim(vl) + ")")));
        }
    }

    private String trim(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 向所有有 kac.notify 权限且开启告警的玩家广播 */
    private void broadcastAlert(PlayerData data, CheckType type, double vl, String info, String world) {
        String msg = ChatColor.translateAlternateColorCodes('&',
                String.format("§8[§cKAC§8] §e%s §7触发 §c%s§8(§7vl:%.1f§8)§7 [%s] %s",
                        data.getName(), type.getDisplayName(), vl, world, info));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("kac.notify")) {
                PlayerData pd = getPlayerData(p);
                if (pd != null && pd.alertsEnabled) {
                    p.sendMessage(msg);
                }
            }
        }
    }

    private String formatLoc(Player p) {
        return String.format("%s %.0f,%.0f,%.0f",
                p.getLocation().getWorld() == null ? "?" : p.getLocation().getWorld().getName(),
                p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
    }

    // ---------- Tick ----------

    public long getTick() {
        return tick;
    }

    public void tickAll() {
        tick++;
        double vlDecay = plugin.getConfigManager().getVlDecay();
        for (PlayerData d : playerData.values()) {
            if (d.velocityExemptTicks > 0) {
                d.velocityExemptTicks--;
            }
            if (d.teleportExemptTicks > 0) {
                d.teleportExemptTicks--;
            }
            // 每 200 tick(10秒) 对违规值做一次衰减，偶发误报不会长期累积
            if (vlDecay > 0 && tick % 200 == 0) {
                d.decayVl(vlDecay);
            }
        }
        for (Check c : checks) {
            try {
                c.onTick();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- 统计与持久化 ----------

    public int getTotalFlags(CheckType type) {
        return totalFlags.getOrDefault(type, 0);
    }

    /** 保存所有在线玩家 vl 到 data/vl.yml */
    public void saveAllData() {
        File file = new File(plugin.getDataFolder(), "data" + File.separator + "vl.yml");
        YamlConfiguration yml = new YamlConfiguration();
        for (PlayerData d : playerData.values()) {
            d.serialize(yml.createSection("players." + d.getUuid().toString()));
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存违规数据失败: " + e.getMessage());
        }
    }

    /** 载入离线玩家数据(供 /kac info 查询) */
    public PlayerData loadPlayerData(UUID uuid, String name) {
        File file = new File(plugin.getDataFolder(), "data" + File.separator + "vl.yml");
        if (file.exists()) {
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                String path = "players." + uuid.toString();
                if (yml.contains(path)) {
                    PlayerData d = new PlayerData(uuid, name);
                    d.deserialize(yml.getConfigurationSection(path));
                    return d;
                }
            } catch (Exception ignored) {
            }
        }
        return new PlayerData(uuid, name);
    }
}
