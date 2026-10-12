package com.keran.kac.check.world;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.Check;
import com.keran.kac.check.CheckType;
import com.keran.kac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Async chat uses its own bounded state and immutable main-thread policy snapshots. */
public final class ChatCheck extends Check {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile Set<UUID> eligible = Set.of();
    private volatile int maximum = 4;
    private static final class State { long window, muted; int count; }
    public ChatCheck(KeranAntiCheat plugin) { super(plugin, CheckType.CHAT); }
    @Override public void onTick() {
        Set<UUID> ids = new HashSet<>();
        if (plugin.getCheckManager().isActive(type)) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers())
                if (!plugin.getCheckManager().isExempt(p, type)) ids.add(p.getUniqueId());
        }
        maximum = Math.max(1, plugin.getConfigManager().getCheckInt(type, "max-chats-per-second", 4));
        eligible = Set.copyOf(ids);
        states.keySet().removeIf(id -> !ids.contains(id));
    }
    @Override public void onChat(AsyncPlayerChatEvent e, PlayerData unused) {
        UUID id = e.getPlayer().getUniqueId();
        if (!eligible.contains(id)) return;
        State s = states.computeIfAbsent(id, k -> new State());
        boolean flag = false;
        synchronized (s) {
            long now = System.currentTimeMillis();
            if (s.muted > now) { e.setCancelled(true); return; }
            if (now - s.window >= 1000) { s.window = now; s.count = 0; }
            if (++s.count > maximum) { s.muted = now + 10000; e.setCancelled(true); flag = true; }
        }
        if (flag) Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = plugin.getCheckManager().getPlayerData(id);
            if (data != null) flag(data, "聊天刷屏，已禁言10秒", 1.0);
        });
    }
    @Override public long eventMask() { return EV_CHAT | EV_TICK; }
}
