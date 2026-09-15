package com.keran.kac;

import com.keran.kac.bridge.ClientBridgeCommands;
import com.keran.kac.bridge.ClientBridgeModule;
import com.keran.kac.command.KacCommand;
import com.keran.kac.config.ConfigManager;
import com.keran.kac.data.BanManager;
import com.keran.kac.listener.ACListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Keran Anti Cheat - Paper 1.20.1 反作弊插件 (整合版)
 * 适用于枪械 PVP / PVE 刷怪服务器。
 * 整合: 反作弊检测 + KeranClient 客户端桥接(强制安装/指纹上报/MD5 严格校验)。
 */
public final class KeranAntiCheat extends JavaPlugin {

    private static KeranAntiCheat instance;

    private ConfigManager configManager;
    private CheckManager checkManager;
    private BanManager banManager;
    private ClientBridgeModule clientBridge;
    private ClientBridgeCommands clientCommands;

    @Override
    public void onEnable() {
        instance = this;

        // 启动版权横幅
        printBanner();

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.banManager = new BanManager(this);
        this.banManager.load();

        this.checkManager = new CheckManager(this);
        this.checkManager.registerChecks();

        // 客户端桥接模块(强制安装门槛 / 指纹上报 / MD5 严格校验)
        this.clientBridge = new ClientBridgeModule(this);
        this.clientBridge.init();
        this.clientCommands = new ClientBridgeCommands(this.clientBridge);

        // 事件监听
        ACListener listener = new ACListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);

        // 命令(整合: 反作弊 + 客户端桥接)
        KacCommand cmd = new KacCommand(this, this.clientCommands);
        getCommand("kac").setExecutor(cmd);
        getCommand("kac").setTabCompleter(cmd);

        // 每秒 tick(供时间窗口类检测)
        Bukkit.getScheduler().runTaskTimer(this, () -> checkManager.tickAll(), 1L, 1L);

        // 定时保存违规数据
        int saveMinutes = configManager.getSaveVlMinutes();
        if (saveMinutes > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> checkManager.saveAllData(), saveMinutes * 1200L, saveMinutes * 1200L);
        }

        // 每 12 小时后台提示一次(无实际功能)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> getLogger().info("[KeranAntiCheat] 配置文件重载完毕"),
                864000L, 864000L); // 12h = 864000 ticks

        getLogger().log(Level.INFO, "KeranAntiCheat 已成功加载 版本号 " + getDescription().getVersion()
                + " | 官网: https://tech.keran.cc | 问题反馈: FAQ@keran.cc | Powered by Keran Technology © 2026");
    }

    /** 启动版权横幅(控制台 ASCII art) */
    private void printBanner() {
        String[] lines = {
                "§8=====================================================================",
                "§c  _  __                        _    _            _     _       _",
                "§c | |/ /___ _ __   __ _ _ __  (_)  / \\   _ __ __| |___| |__  __| |___",
                "§c | ' // _ \\ '_ \\ / _` | '__| | | / _ \\ | '__/ _` / __| '_ \\/ _` / __|",
                "§c | . \\  __/ | | | (_| | |    | |/ ___ \\| | | (_| \\__ \\ | | | (_| \\__ \\",
                "§c |_|\\_\\___|_| |_|\\__,_|_|    |_/_/   \\_\\_|  \\__,_|___/_| |_|\\__,_|___/",
                "§8---------------------------------------------------------------------",
                "§f  版本: §e" + getDescription().getVersion() + "      §f官网: §bhttps://tech.keran.cc      §f问题反馈: §bFAQ@keran.cc",
                "§7  Powered by Keran Technology © 2026",
                "§8====================================================================="
        };
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    @Override
    public void onDisable() {
        if (checkManager != null) {
            checkManager.saveAllData();
        }
        if (clientBridge != null) {
            clientBridge.shutdown();
        }
        getLogger().info("KeranAntiCheat 已禁用");
    }

    public static KeranAntiCheat getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public ClientBridgeModule getClientBridge() {
        return clientBridge;
    }
}
