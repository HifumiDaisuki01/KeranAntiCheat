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

import java.util.List;

/**
 * 集中事件监听：把事件路由到各检测器。
 *
 * <p><b>v3.1 (KAC-06) 事件路由重构</b>：v3.0 对每个事件都遍历全部 28 个检测器，
 * 其中未覆写该事件的检测器只是空跑基类空实现；且每个检测器还要独立调用一次
 * {@code isExempt}（内部最多 4 次权限查询）。战斗场景下 {@code PlayerMoveEvent}
 * 每秒触发数十次，产生大量无意义的空转与权限查询。
 *
 * <p>v3.1 改为：路由时通过 {@link com.keran.kac.CheckManager#checksFor(long)}
 * 只取<b>真正订阅了该事件</b>的检测器；{@code isExempt} 结果按 tick 缓存。
 */
public class ACListener implements Listener {

    private final KeranAntiCheat plugin;

    public ACListener(KeranAntiCheat plugin) {
        this.plugin = plugin;
    }

    /**
     * 遍历订阅了指定事件的检测器并逐个调用 {@code action}。
     * 自动跳过被禁用/被豁免的检测。
     */
    private void route(Player p, long eventFlag, CheckAction action) {
        List<Check> list = plugin.getCheckManager().checksFor(eventFlag);
        if (list.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Check c = list.get(i);
            // isActive = 配置启用 且 未被 /kac toggle 关闭
            if (!plugin.getCheckManager().isActive(c.getType())) {
                continue;
            }
            if (plugin.getCheckManager().isExempt(p, c.getType())) {
                continue;
            }
            try {
                action.run(c);
            } catch (Exception error) {
                plugin.getCheckManager().reportError(c, error);
            }
        }
    }

    /** 检测器回调 */
    @FunctionalInterface
    private interface CheckAction {
        void run(Check c);
    }

    /** 取玩家数据，拿不到则返回 null */
    private PlayerData dataOf(Player p) {
        return plugin.getCheckManager().getPlayerData(p);
    }

    // ---------- 生命周期 ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent e) {
        if (plugin.getBanManager().isBanned(e.getPlayer().getUniqueId())) {
            String info = plugin.getBanManager().getBanInfo(e.getPlayer().getUniqueId());
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            "§c[KAC] 你已被封禁\n§7" + info));
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

    // Strict admission has a grace period for scanning, but does not allow gameplay before verification.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gateMove(PlayerMoveEvent e) {
        if (!plugin.getClientBridge().canPlay(e.getPlayer()) && e.getTo() != null
                && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY()
                    || e.getFrom().getZ() != e.getTo().getZ())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gateDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && !plugin.getClientBridge().canPlay(p)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player p
                && !plugin.getClientBridge().canPlay(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gateInteract(PlayerInteractEvent e) {
        if (!plugin.getClientBridge().canPlay(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gateBreak(BlockBreakEvent e) {
        if (!plugin.getClientBridge().canPlay(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gatePlace(BlockPlaceEvent e) {
        if (!plugin.getClientBridge().canPlay(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void gateInventory(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && !plugin.getClientBridge().canPlay(p)) e.setCancelled(true);
    }

    // ---------- 移动 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().equals(e.getTo())) {
            return;
        }
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        // ---- 更新通用移动状态（供各检测器共享） ----
        data.lastDx = e.getTo().getX() - e.getFrom().getX();
        data.lastDy = e.getTo().getY() - e.getFrom().getY();
        data.lastDz = e.getTo().getZ() - e.getFrom().getZ();
        data.lastMoveTick = plugin.getCheckManager().getTick();
        data.lastMoveTime = System.currentTimeMillis();

        // 跳跃判定：从地面转为空中且垂直速度为正
        boolean nowGround = p.isOnGround();
        if (data.lastTickWasGround && !nowGround && data.lastDy > 0.3) {
            data.sinceJumpTicks = 0;
            data.lastJumpY = e.getFrom().getY();
        } else {
            data.sinceJumpTicks++;
        }
        data.lastTickWasGround = nowGround;
        data.wasOnGround = nowGround;

        route(p, Check.EV_MOVE, c -> c.onMove(e, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackRequest(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        PlayerData d = dataOf(e.getPlayer());
        if (d != null && e.willAttack())
            d.recordAttackRequest(e.getAttacked().getUniqueId(), plugin.getCheckManager().getTick());
    }

    // ---------- 战斗 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (!(damager instanceof Player)) {
            return;
        }
        Player p = (Player) damager;
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (plugin.getBehaviorContext().isSpecialDamage(p)) return;
        if (!data.consumeAttackRequest(e.getEntity().getUniqueId(), plugin.getCheckManager().getTick())) return;
        route(p, Check.EV_DAMAGE_DEALT, c -> c.onDamageDealt(e, data));
        data.lastCombatTick = plugin.getCheckManager().getTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        if (e.getFinalDamage() > 0) data.lastDamageTick = plugin.getCheckManager().getTick();
        route(p, Check.EV_DAMAGE_TAKEN, c -> c.onDamageTaken(e, data));
    }

    // ---------- 世界 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        route(p, Check.EV_BREAK, c -> c.onBreak(e, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        route(p, Check.EV_PLACE, c -> c.onPlace(e, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        route(p, Check.EV_INTERACT, c -> c.onInteract(e, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        route(p, Check.EV_CONSUME, c -> c.onConsume(e, data));
    }

    // ---------- 聊天(异步, 检测与禁言) ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Check c = plugin.getCheckManager().getCheck(CheckType.CHAT);
        if (c != null) c.onChat(e, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        PlayerData d = dataOf(p);
        if (d != null) route(p, Check.EV_INVENTORY, c -> c.onInventory(e, d));
    }

    // ---------- 击退 / 传送 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        data.lastVelocityTick = plugin.getCheckManager().getTick();
        data.velocityExemptTicks = plugin.getConfigManager().getVelocityExemptTicks();
        route(p, Check.EV_VELOCITY, c -> c.onVelocity(e, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        PlayerData data = dataOf(p);
        if (data == null) {
            return;
        }
        data.teleportExemptTicks = 5;
        data.rotationDeltas.clear(); data.yawDeltas.clear();
        data.lastYaw = e.getTo().getYaw(); data.lastPitch = e.getTo().getPitch();
        data.falling = false; data.damageLocation = null; data.expectedKbSpeed = 0;
        data.checkBuffer.clear(); data.checkConsecutive.clear();
        route(p, Check.EV_TELEPORT, c -> c.onTeleport(e, data));
    }
}
