package com.keran.kac.api;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.check.CheckType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

/** Server-only, scoped and expiring declarations for custom gameplay. No client can grant exemptions. */
public final class BehaviorContext {
    private final KeranAntiCheat plugin;
    private final Map<UUID, Map<CheckType, Long>> movement = new HashMap<>();
    private final Map<UUID, Long> specialDamage = new HashMap<>();
    public BehaviorContext(KeranAntiCheat plugin) { this.plugin = plugin; }
    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call on the server thread");
    }
    public void exemptMovement(Player player, String source, int ticks, Set<CheckType> checks) {
        requireServerThread();
        if (source == null || source.isBlank() || ticks < 1 || ticks > 1200)
            throw new IllegalArgumentException("Named source and duration 1..1200 ticks required");
        Map<CheckType, Long> scoped = movement.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(CheckType.class));
        for (CheckType check : checks) scoped.merge(check, plugin.getCheckManager().getTick() + ticks, Math::max);
    }
    public void markSpecialDamage(Player player, String source, int ticks) {
        requireServerThread();
        if (source == null || source.isBlank() || ticks < 1 || ticks > 20) throw new IllegalArgumentException("Duration 1..20 required");
        specialDamage.merge(player.getUniqueId(), plugin.getCheckManager().getTick() + ticks, Math::max);
    }
    public boolean isMovementExempt(UUID player, CheckType type) {
        Map<CheckType, Long> scoped = movement.get(player);
        return scoped != null && scoped.getOrDefault(type, -1L) > plugin.getCheckManager().getTick();
    }
    public boolean isSpecialDamage(Player player) {
        return specialDamage.getOrDefault(player.getUniqueId(), -1L) > plugin.getCheckManager().getTick();
    }
    public void remove(UUID player) { movement.remove(player); specialDamage.remove(player); }
}
