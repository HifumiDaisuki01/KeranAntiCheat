package com.keran.kac;

import com.keran.kac.command.KacCommand;
import com.keran.kac.config.ConfigManager;
import com.keran.kac.data.BanManager;
import com.keran.kac.listener.ACListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Keran Anti Cheat - Paper 1.16.5 反作弊插件
 * 适用于枪械 PVP / PVE 刷怪服务器。
 */
public final class KeranAntiCheat extends JavaPlugin {

    private static KeranAntiCheat instance;

    private ConfigManager configManager;
    private CheckManager checkManager;
    private BanManager banManager;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.banManager = new BanManager(this);
        this.banManager.load();

        this.checkManager = new CheckManager(this);
        this.checkManager.registerChecks();

        // 事件监听
        ACListener listener = new ACListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);

        // 命令
        KacCommand cmd = new KacCommand(this);
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

        getLogger().log(Level.INFO, "Keran Anti Cheat v" + getDescription().getVersion() + " 已启用 (作者: Keran Technology Co., Ltd.)");
    }

    @Override
    public void onDisable() {
        if (checkManager != null) {
            checkManager.saveAllData();
        }
        getLogger().info("Keran Anti Cheat 已禁用");
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
}
