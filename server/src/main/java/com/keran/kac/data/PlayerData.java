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
    private final Map<CheckType, Double> vl = new EnumMap<>(CheckType.class);
    /** 各检测器告警次数 */
    private final Map<CheckType, Integer> flags = new EnumMap<>(CheckType.class);
    /** 各检测器上次告警时间戳 */
    private final Map<CheckType, Long> lastNotify = new EnumMap<>(CheckType.class);
    /** 各检测器处罚次数(踢出/封禁) */
    private final Map<CheckType, Integer> punishments = new EnumMap<>(CheckType.class);

    // ==================== 置信度模型状态(v3.0) ====================
    /** 各检测器累积证据分（达到 alert-threshold 才上报） */
    public final Map<CheckType, Double> checkBuffer = new EnumMap<>(CheckType.class);
    /** 各检测器连续异常次数（连续帧验证） */
    public final Map<CheckType, Integer> checkConsecutive = new EnumMap<>(CheckType.class);

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
    public double lastDx = 0;             // 最近一次移动的水平位移(Step)
    public double lastDz = 0;
    public boolean lastTickWasGround = true; // 上一 tick 是否在地面(GroundSpoof)

    // ---- 移动增强(v3.0) ----
    public int airSprintTicks = 0;        // 连续空中疾跑 tick
    public double maxAirSpeed = 0;        // 空中最大水平速度
    public long lastGroundTick = 0;       // 上次在地面的 tick
    public double lastJumpY = 0;          // 最近跳跃起始 y
    public int sinceJumpTicks = 0;        // 距上次跳跃的 tick 数
    public int groundSpoofTicks = 0;      // 连续伪造落地 tick
    public int positionPackets = 0;       // 本窗口位置包
    public int rotationPackets = 0;       // 本窗口视角包(Timer 区分用)
    public long packetWindowStart = 0;
    public int noSlowTicks = 0;           // 使用物品中未减速 tick
    public long lastUseItemTime = 0;      // 上次使用物品时间
    public double lastStepHeight = 0;     // 单 tick 上升高度(Step)
    public int iceTicks = 0;              // 冰面滑行计时(速度检测豁免缓冲)
    /** 上次处理移动事件的 tick(Blink 用它把位移归一化为"格/tick", v3.1 新增) */
    public long lastBlinkTick = 0;

    // ---- 战斗检测状态 ----
    public long lastAttackTime = 0;
    public int cpsClicks = 0;             // 本窗口点击数(AutoClicker)
    public long cpsWindowStart = 0;
    public final java.util.Deque<Double> recentAttackY = new java.util.ArrayDeque<>();
    public final java.util.Deque<Boolean> recentAttackGround = new java.util.ArrayDeque<>();
    public int critStreak = 0;            // 连续暴击次数
    public long lastCritTime = 0;
    public double lastAttackAngle = 0;    // 上次攻击夹角(KillAura 平滑度)
    public long lastTargetSwitchTick = 0; // 上次切换目标 tick
    public int targetsSwitchedFast = 0;   // 快速切换目标计数
    public Location lastAttackTargetLoc = null; // 上次攻击目标位置(KillAura 多目标分散度)
    public final java.util.Deque<Double> rotationDeltas = new java.util.ArrayDeque<>(); // 视角增量历史(Aim)
    /** 带符号 yaw 增量历史(Aim, 用于方向反转率分析, v3.1 新增) */
    public final java.util.Deque<Double> yawDeltas = new java.util.ArrayDeque<>();
    public int aimStreak = 0;             // 连续异常视角次数
    public double lastAttackReach = 0;    // 上次攻击距离(Reach 趋势)
    public int reachStreak = 0;           // 连续超距次数
    public int hitboxStreak = 0;          // 连续命中箱异常
    public double lastHitOffset = 0;      // 上次命中偏移(命中箱)
    public int blockingAttackCount = 0;   // 攻击时仍在格挡计数(AutoBlock)
    public long lastBlockTime = 0;
    public int kbResistStreak = 0;        // 连续无击退次数(AntiKB)
    public long lastKbCheckTick = 0;      // 上次击退检查 tick
    public final java.util.Deque<Long> attackTimestamps = new java.util.ArrayDeque<>(); // 攻击时间戳
    public final java.util.Deque<Long> attackIntervals = new java.util.ArrayDeque<>();   // 攻击间隔(AutoClicker 方差)

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
    public final java.util.Deque<Integer> oreStreak = new java.util.ArrayDeque<>();     // 连续挖矿序列
    public final java.util.Deque<Double> oreDepths = new java.util.ArrayDeque<>();      // 挖矿深度历史
    public int oresWithoutStone = 0;      // 未挖石头直接命中矿石计数(XRay)
    public final java.util.Deque<Long> breakTimestamps = new java.util.ArrayDeque<>();  // 破坏时间戳
    public int inventoryOpsThisSecond = 0;
    public long inventoryWindowStart = 0;

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

    public double getVl(CheckType t) {
        return vl.getOrDefault(t, 0.0);
    }

    /** 兼容旧调用: 取整后返回 */
    public int getVlInt(CheckType t) {
        return (int) Math.round(vl.getOrDefault(t, 0.0));
    }

    public void addVl(CheckType t, double amount) {
        vl.merge(t, amount, Double::sum);
    }

    public void resetVl(CheckType t) {
        vl.put(t, 0.0);
    }

    public void resetAllVl() {
        vl.clear();
    }

    /**
     * 对所有检测器的违规值做一次时间衰减。
     * 老模型 VL 只增不减，偶发误报会长期累积最终导致误封；本版让 VL 随时间自然回落。
     *
     * @param amount 每次衰减量
     */
    public void decayVl(double amount) {
        if (amount <= 0) {
            return;
        }
        for (Map.Entry<CheckType, Double> e : vl.entrySet()) {
            double v = e.getValue() - amount;
            e.setValue(v > 0 ? v : 0.0);
        }
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
            vl.put(t, sec.getDouble("vl." + t.getId(), 0.0));
            flags.put(t, sec.getInt("flags." + t.getId(), 0));
        }
    }
}
