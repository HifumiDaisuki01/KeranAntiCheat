package com.keran.kac.config;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.CheckType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 配置文件管理：默认值、读写、运行时重载。
 */
public class ConfigManager {

    private final KeranAntiCheat plugin;
    private FileConfiguration config;
    private File configFile;

    // 常用全局设置缓存
    private boolean bypassAdmin;
    private boolean bypassOp;
    private boolean verbose;
    private int notifyCooldown;
    private int saveVlMinutes;
    private int defaultKickVl;
    private int defaultBanVl;
    private int velocityExemptTicks;
    /** 证据分衰减率（每次"正常观测"削减多少证据分） */
    private double decayRate;
    /** 违规值时间衰减量（每 10 秒削减多少 VL） */
    private double vlDecay;

    public ConfigManager(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    /** 加载/生成 config.yml */
    public void load() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // 与 jar 内默认配置合并, 保证新增键有默认值
        InputStream def = plugin.getResource("config.yml");
        if (def != null) {
            try (InputStreamReader reader = new InputStreamReader(def, StandardCharsets.UTF_8)) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
                config.options().copyDefaults(true);
            } catch (Exception ignored) {
            }
        }
        reloadCache();
    }

    public void reload() {
        load();
    }

    /** 把常用设置读入缓存 */
    private void reloadCache() {
        bypassAdmin = config.getBoolean("settings.bypass-admin", true);
        bypassOp = config.getBoolean("settings.bypass-op", true);
        verbose = config.getBoolean("settings.verbose", false);
        notifyCooldown = config.getInt("settings.notify-cooldown-seconds", 3);
        saveVlMinutes = config.getInt("settings.save-vl-minutes", 5);
        defaultKickVl = config.getInt("punishments.default-kick-vl", 20);
        defaultBanVl = config.getInt("punishments.default-ban-vl", 40);
        velocityExemptTicks = config.getInt("settings.velocity-exempt-ticks", 12);
        decayRate = config.getDouble("settings.evidence-decay-rate", 0.35);
        vlDecay = config.getDouble("settings.vl-decay-per-10s", 0.5);
    }

    public FileConfiguration get() {
        return config;
    }

    public boolean isBypassAdmin() {
        return bypassAdmin;
    }

    public boolean isBypassOp() {
        return bypassOp;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean v) {
        verbose = v;
    }

    public int getNotifyCooldownSeconds() {
        return notifyCooldown;
    }

    public int getSaveVlMinutes() {
        return saveVlMinutes;
    }

    public int getVelocityExemptTicks() {
        return velocityExemptTicks;
    }

    /** 证据分衰减率：每次"正常观测"削减多少证据分（越大越不容易误报） */
    public double getDecayRate() {
        return decayRate;
    }

    /** 违规值时间衰减量：每 10 秒削减多少 VL（0 = 不衰减） */
    public double getVlDecay() {
        return vlDecay;
    }

    /** 受伤后被击退/推动的豁免 tick 数（飞行检测等使用） */
    public int getDamageExemptTicks() {
        return config.getInt("settings.damage-exempt-ticks", 25);
    }

    /** 延迟补偿比例：每 100ms 延迟放宽多少阈值（0.15 = 15%） */
    public double getLagCompensationRatio() {
        return config.getDouble("settings.lag-compensation-ratio", 0.15);
    }

    /** 离开冰面后的滑行缓冲 tick 数（冰面滑行惯性会持续，需豁免） */
    public int getIceBufferTicks() {
        return config.getInt("settings.ice-buffer-ticks", 12);
    }

    /** Reach 延迟补偿比例：每 100ms 延迟放宽多少射程（0.12 = 12%） */
    public double getReachLagRatio() {
        return config.getDouble("settings.reach-lag-ratio", 0.12);
    }

    /** 检测器是否启用 */
    public boolean isCheckEnabled(CheckType t) {
        return config.getBoolean("checks." + t.getId() + ".enabled", true);
    }

    /** 检测器是否被运行时关闭(与配置合并) */
    public boolean isCheckDisabledRuntime(CheckType t) {
        return plugin.getCheckManager().isDisabledByCommand(t);
    }

    public boolean isCheckActive(CheckType t) {
        return isCheckEnabled(t) && !isCheckDisabledRuntime(t);
    }

    /** 读取检测器配置整数值 */
    public int getCheckInt(CheckType t, String key, int def) {
        return config.getInt("checks." + t.getId() + "." + key, def);
    }

    public double getCheckDouble(CheckType t, String key, double def) {
        return config.getDouble("checks." + t.getId() + "." + key, def);
    }

    public boolean getCheckBoolean(CheckType t, String key, boolean def) {
        return config.getBoolean("checks." + t.getId() + "." + key, def);
    }

    /** 踢出阈值(检测器单独配置优先, 否则用全局默认) */
    public int getKickVl(CheckType t) {
        int v = config.getInt("checks." + t.getId() + ".kick-vl", -1);
        return v >= 0 ? v : defaultKickVl;
    }

    /** 封禁阈值 */
    public int getBanVl(CheckType t) {
        int v = config.getInt("checks." + t.getId() + ".ban-vl", -1);
        return v >= 0 ? v : defaultBanVl;
    }

    public String getKickMessage() {
        return config.getString("messages.kick", "§c[KAC] 你已被移出服务器\n§7原因: %reason%");
    }

    public String getBanMessage() {
        return config.getString("messages.ban", "§c[KAC] 你已被封禁\n§7原因: %reason%");
    }
}
