package com.keran.kac.data;

import com.keran.kac.check.CheckType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个在线玩家一份的违规数据与检测状态。
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;

    /** 各检测器违规值 */
    private final Map<CheckType, Integer> vl = new EnumMap<>(CheckType.class);
    /** 各检测器告警次数 */
    private final Map<CheckType, Integer> flags = new EnumMap<>(CheckType.class);
    /** 各检测器上次告警时间戳 */
    private final Map<CheckType, Long> lastNotify = new EnumMap<>(CheckType.class);
    /** 各检测器处罚次数(踢出/封禁) */
    private final Map<CheckType, Integer> punishments = new EnumMap<>(CheckType.class);

    // ---- 移动检测状态 ----
    public int airTicks = 0;              // 连续滞空 tick
    public int ascendTicks = 0;           // 连续上升 tick
    public double takeoffY = 0;           // 离开地面时的 y
    public boolean inAirSince = false;    // 是否正处于滞空
    public int hoverTicks = 0;            // 悬浮 tick(垂直位移≈0)
    public int liquidTicks = 0;           // 液体中悬浮 tick
    public int wallTicks = 0;             // 贴墙上升 tick
    public int velocityExemptTicks = 0;   // 击退豁免剩余 tick
    public int teleportExemptTicks = 0;   // 传送豁免剩余 tick
    public long lastMoveTime = -1;
    public long lastMoveTick = 0;
    public int movesThisSecond = 0;       // 本秒移动次数(Timer)
    public long timerWindowStart = 0;
    public double lastYaw = 0;
    public double lastPitch = 0;
    public long lastRotationTime = 0;
    public double lastHorizSpeed = 0;
    public boolean wasOnGround = true;
    public double fallStartY = 0;
    public boolean falling = false;
    public double lastGroundY = 0;
    public long lastDamageTick = -10;     // 上次受伤 tick(用于击退/被攻击状态)
    public Location damageLocation = null; // 受伤瞬间位置(防击退检测)
    public long lastAttackTick = -10;     // 上次攻击 tick(KillAura 多目标)
    public int attacksSameTick = 0;
    public long lastVelocityTick = -10;
    public double lastFallDistance = 0;
    public long lastFallDamageTime = 0;   // 最近一次摔落伤害时间戳(NoFall)
    public double lastDy = 0;             // 最近一次移动的垂直位移(Criticals)

    // ---- 战斗检测状态 ----
    public long lastAttackTime = 0;
    public int cpsClicks = 0;             // 本窗口点击数(AutoClicker)
    public long cpsWindowStart = 0;
    public final java.util.Deque<Double> recentAttackY = new java.util.ArrayDeque<>(); // 攻击前 y 历史(Criticals)
    public final java.util.Deque<Boolean> recentAttackGround = new java.util.ArrayDeque<>();

    // ---- 世界检测状态 ----
    public int totalMined = 0;            // 总挖掘方块数(XRay)
    public int oreMined = 0;              // 挖到的矿物数
    public long breakWindowStart = 0;
    public int breaksThisSecond = 0;      // 本秒破坏数
    public int breaksSameTick = 0;        // 同 tick 破坏数
    public long lastBreakTick = -10;
    public Location lastBreakLoc = null;
    public long lastPlaceTime = 0;
    public int placesThisSecond = 0;      // 本秒放置数
    public long placeWindowStart = 0;
    public long lastConsumeTime = 0;
    public int consumesThisSecond = 0;
    public long consumeWindowStart = 0;

    // ---- 聊天状态 ----
    public long lastChatTime = 0;
    public int chatsThisSecond = 0;
    public long chatWindowStart = 0;
    public long muteUntil = 0;            // 禁言截止时间戳

    // ---- 通知偏好 ----
    public boolean alertsEnabled = true;

    public PlayerData(Player p) {
        this(p.getUniqueId(), p.getName());
        this.lastYaw = p.getLocation().getYaw();
        this.lastPitch = p.getLocation().getPitch();
        this.wasOnGround = p.isOnGround();
    }

    /** 无在线玩家的构造(离线数据查询用) */
    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getVl(CheckType t) {
        return vl.getOrDefault(t, 0);
    }

    public void addVl(CheckType t, int amount) {
        vl.merge(t, amount, Integer::sum);
    }

    public void resetVl(CheckType t) {
        vl.put(t, 0);
    }

    public void resetAllVl() {
        vl.clear();
    }

    public int getFlags(CheckType t) {
        return flags.getOrDefault(t, 0);
    }

    public void addFlag(CheckType t) {
        flags.merge(t, 1, Integer::sum);
    }

    public int getPunishments(CheckType t) {
        return punishments.getOrDefault(t, 0);
    }

    public void addPunishment(CheckType t) {
        punishments.merge(t, 1, Integer::sum);
    }

    public long getLastNotify(CheckType t) {
        return lastNotify.getOrDefault(t, 0L);
    }

    public void setLastNotify(CheckType t, long time) {
        lastNotify.put(t, time);
    }

    /** 保存到配置节(供 /kac info 与重启保留) */
    public void serialize(org.bukkit.configuration.ConfigurationSection sec) {
        sec.set("name", name);
        for (CheckType t : CheckType.values()) {
            if (getVl(t) > 0) {
                sec.set("vl." + t.getId(), getVl(t));
            }
            if (getFlags(t) > 0) {
                sec.set("flags." + t.getId(), getFlags(t));
            }
        }
    }

    /** 从配置节载入(仅 vl 与 flags) */
    public void deserialize(org.bukkit.configuration.ConfigurationSection sec) {
        for (CheckType t : CheckType.values()) {
            vl.put(t, sec.getInt("vl." + t.getId(), 0));
            flags.put(t, sec.getInt("flags." + t.getId(), 0));
        }
    }
}
