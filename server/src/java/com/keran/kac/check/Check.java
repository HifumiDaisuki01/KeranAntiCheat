package com.keran.kac.check;

import com.keran.kac.KeranAntiCheat;
import com.keran.kac.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * 检测器抽象基类。
 * 所有反作弊检测继承本类，只覆写自己关心的事件回调。
 */
public abstract class Check {

    protected final KeranAntiCheat plugin;
    protected final CheckType type;

    public Check(KeranAntiCheat plugin, CheckType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public CheckType getType() {
        return type;
    }

    public String getId() {
        return type.getId();
    }

    /** 该检测当前是否启用(读取配置) */
    public boolean isEnabled() {
        return plugin.getConfigManager().isCheckEnabled(type);
    }

    /** 触发违规上报：加违规值、告警、按阶梯执行处罚 */
    public final void flag(PlayerData data, String info, int addVl) {
        plugin.getCheckManager().handleFlag(data, type, info, addVl);
    }

    /** 快捷方式: 由玩家对象获取 PlayerData 后上报 */
    public final void flag(Player player, String info, int addVl) {
        PlayerData data = plugin.getCheckManager().getPlayerData(player);
        if (data != null) {
            flag(data, info, addVl);
        }
    }

    /** 移动类检测的通用临时豁免(击退中/传送后) */
    protected final boolean isMovementExempt(PlayerData data) {
        return data.velocityExemptTicks > 0 || data.teleportExemptTicks > 0;
    }

    /** 数字格式化(保留2位) */
    protected final String trim(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ---- 事件回调(按需覆写) ----

    public void onMove(PlayerMoveEvent e, PlayerData data) {
    }

    public void onDamageDealt(EntityDamageByEntityEvent e, PlayerData data) {
    }

    public void onDamageTaken(EntityDamageEvent e, PlayerData data) {
    }

    public void onBreak(BlockBreakEvent e, PlayerData data) {
    }

    public void onPlace(BlockPlaceEvent e, PlayerData data) {
    }

    public void onInteract(PlayerInteractEvent e, PlayerData data) {
    }

    public void onConsume(PlayerItemConsumeEvent e, PlayerData data) {
    }

    public void onChat(AsyncPlayerChatEvent e, PlayerData data) {
    }

    public void onVelocity(PlayerVelocityEvent e, PlayerData data) {
    }

    public void onTeleport(PlayerTeleportEvent e, PlayerData data) {
    }

    /** 每秒 tick 回调(用于时间窗口类统计), 在同步线程执行 */
    public void onTick() {
    }
}
