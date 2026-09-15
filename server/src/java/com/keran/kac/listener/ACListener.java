package com.keran.kac.listener;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

/**
 * 集中事件监听：把事件路由到各检测器。
 */
public class ACListener implements Listener {

    private final KeranAntiCheat plugin;

    public ACListener(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    private void route(Player p, CheckType type, Runnable r) {
        if (plugin.getCheckManager().isActive(type)
                && !plugin.getCheckManager().isExempt(p, type)) {
            r.run();
        }
    }

    // ---------- 生命周期 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent e) {
        if (plugin.getBanManager().isBanned(e.getPlayer().getUniqueId())) {
            String info = plugin.getBanManager().getBanInfo(e.getPlayer().getUniqueId());
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            "§c[KeranAntiCheat] 你已被封禁\n§7" + info));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getCheckManager().addPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getCheckManager().removePlayer(e.getPlayer());
    }

    // ---------- 移动 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().equals(e.getTo())) {
            return;
        }
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        data.lastDy = e.getTo().getY() - e.getFrom().getY();
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onMove(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- 战斗 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (!(damager instanceof Player)) {
            return;
        }
        Player p = (Player) damager;
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onDamageDealt(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onDamageTaken(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- 世界 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onBreak(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onPlace(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onInteract(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onConsume(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---------- 聊天(异步, 检测与禁言) ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        // 禁言拦截(异步线程, 发消息切主线程)
        if (data.muteUntil > System.currentTimeMillis()) {
            e.setCancelled(true);
            final Player fp = p;
            final long remain = (data.muteUntil - System.currentTimeMillis()) / 1000 + 1;
            Bukkit.getScheduler().runTask(plugin, () -> fp.sendMessage(
                    org.bukkit.ChatColor.RED + "你正在被禁言中, 剩余 " + remain + " 秒"));
            return;
        }
        // 聊天刷屏检测
        if (plugin.getCheckManager().isActive(CheckType.CHAT)
                && !plugin.getCheckManager().isExempt(p, CheckType.CHAT)) {
            for (Check c : plugin.getCheckManager().getChecks()) {
                if (c.getType() == CheckType.CHAT) {
                    try {
                        c.onChat(e, data);
                    } catch (Throwable ignored) {
                    }
                    break;
                }
            }
        }
    }

    // ---------- 击退 / 传送 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        data.lastVelocityTick = plugin.getCheckManager().getTick();
        data.velocityExemptTicks = plugin.getConfigManager().getVelocityExemptTicks();
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onVelocity(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        PlayerData data = plugin.getCheckManager().getPlayerData(p);
        if (data == null) {
            return;
        }
        data.teleportExemptTicks = 5;
        for (Check c : plugin.getCheckManager().getChecks()) {
            if (c.isEnabled() && !plugin.getCheckManager().isExempt(p, c.getType())) {
                try {
                    c.onTeleport(e, data);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
