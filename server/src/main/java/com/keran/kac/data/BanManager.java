package com.keran.kac.data;

import com.keran.kac.KeranAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 封禁管理：bans.yml 持久化, 支持临时/永久封禁与登录拦截。
 */
public class BanManager {

    private final KeranAntiCheat plugin;
    private File file;
    private YamlConfiguration bans;

    public BanManager(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "data" + File.separator + "bans.yml");
        bans = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            file.getParentFile().mkdirs();
            bans.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存封禁数据失败: " + e.getMessage());
        }
    }

    /**
     * 封禁玩家。
     *
     * @param reason 原因
     * @param hours  时长(小时), 0 或负数 = 永久
     */
    public void ban(Player target, String reason, int hours) {
        long expire = hours > 0 ? System.currentTimeMillis() + hours * 3600_000L : -1;
        String path = "bans." + target.getUniqueId().toString();
        bans.set(path + ".name", target.getName());
        bans.set(path + ".reason", reason);
        bans.set(path + ".time", System.currentTimeMillis());
        bans.set(path + ".expire", expire);
        save();
    }

    public void unban(UUID uuid) {
        bans.set("bans." + uuid.toString(), null);
        save();
    }

    /** 检查玩家是否被封禁(过期则自动解封) */
    public boolean isBanned(UUID uuid) {
        String path = "bans." + uuid.toString();
        if (!bans.contains(path)) {
            return false;
        }
        long expire = bans.getLong(path + ".expire", -1);
        if (expire > 0 && System.currentTimeMillis() > expire) {
            bans.set(path, null);
            save();
            return false;
        }
        return true;
    }

    /** 获取封禁剩余时间描述 */
    public String getBanInfo(UUID uuid) {
        String path = "bans." + uuid.toString();
        if (!bans.contains(path)) {
            return null;
        }
        String reason = bans.getString(path + ".reason", "未知原因");
        long expire = bans.getLong(path + ".expire", -1);
        if (expire < 0) {
            return "永久封禁, 原因: " + reason;
        }
        long remainMin = (expire - System.currentTimeMillis()) / 60_000L;
        if (remainMin < 1) {
            remainMin = 1;
        }
        return "剩余 " + remainMin + " 分钟, 原因: " + reason;
    }
}
