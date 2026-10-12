# 安装与严格模式

停服，备份 `plugins/KeranAntiCheat/`，替换服务端 JAR。客户端使用 Fabric 1.20.1、Fabric API 和 KeranClient 3.2.1，移除旧版同名模组。

在 `plugins/KeranAntiCheat/config.yml` 配置：

```yaml
client:
  strict-mode: true
join:
  wait-seconds: 20
heartbeat:
  enabled: true
  timeout-seconds: 90
```

`true`：玩家必须完成本次连接的有效报告；等待期间限制游戏动作，超时断开。验证后心跳中断也会断开。
`false`：允许未安装模组的玩家，移动/近战等服务端检测仍启用。已安装的客户端仍可上报和供管理员查询。
执行 `/kac reload` 可切换；从关闭切到开启，尚未上报的在线玩家获得新的等待期。
`kac.bypass` 或旧版 `kacc.bypass` 可豁免客户端门槛。管理员身份本身不会豁免门槛。
旧版 `join.enabled`、`join.kick-if-no-client` 仅用于首次升级迁移，之后设置 `client.strict-mode`。
客户端白名单为空时允许上报到支持协议的服务器；配置白名单时使用准确主机或IP，可附端口，不支持子串匹配。

严格模式和 `strict-md5.enabled` 是两个不同设置：后者检查已上报的文件摘要。更新客户端后旧MD5基线失效，用管理员 `/kac modsmd5`、`/kac packsmd5` 获取新值。建议先关闭MD5严格校验验证连接。

# 告警与处罚

`kick-vl`/`ban-vl` 为0时继承全局值，-1关闭对应处罚。
`checks.<id>.observe-only: true` 完全禁止该检测自动踢出与封禁，但保留告警和统计。
Aim、AutoClicker、XRay、Backtrack 默认观察。
自动封禁时长由 `punishments.auto-ban-hours` 控制，默认24小时，0永久。

# 通用插件适配

其他插件将 KeranAntiCheat 声明为 softdepend 后，在服务端主线程调用：

```java
KeranAntiCheat kac = (KeranAntiCheat) Bukkit.getPluginManager().getPlugin("KeranAntiCheat");
kac.getBehaviorContext().markSpecialDamage(player, "MyPlugin.skill", 1);
// 紧接着执行自定义玩家归因伤害；本tick不计入原版近战。
kac.getBehaviorContext().exemptMovement(player, "MyPlugin.dash", 10,
    EnumSet.of(CheckType.SPEED, CheckType.FLY, CheckType.BLINK));
```

特殊伤害时长限1..20ticks；移动豁免限1..1200ticks，按指定检测类型生效，退出自动清理。应尽可能缩短范围，避免频繁长时间豁免。
没有真实近战请求的插件伤害不会进入近战检测。在真实近战请求中同步产生的自定义 ENTITY_ATTACK 伤害，可调用上述API明确区分；不能仅凭手持物品豁免。

# 构建和验收

服务端：Java17 + Maven，`mvn -f server/pom.xml verify`。
客户端：Java17，`cd client && ./gradlew build`；Windows运行`gradlew.bat build`。

回归测试覆盖协议帧/深度、准入策略/配置迁移、碰撞箱几何、原子写入与历史恢复。
额外用真实Paper1.20.1和协议测试客户端检查可选模式、正常报告、频道注册绕过、错误会话及心跳超时。
Fabric客户端只做构建/资源校验，没有完成图形游戏客户端和真实枪械插件组合的人工实测。

## 检测器与处罚

目前保留 28 项检测器：

| 分类 | 检测器 |
|---|---|
| 移动（11） | Fly / Speed / Timer / NoFall / Jesus / Spider / Blink / Elytra / NoSlow / Step / GroundSpoof |
| 战斗（10） | KillAura / Aim / Reach / Criticals / AutoClicker / Velocity / AntiKb / Hitbox / Backtrack / AutoBlock |
| 世界与交互（7） | XRay / Nuker / Scaffold / FastUse / FastBreak / Chat / Inventory |

大部分检测采用异常观测、证据累积、加权 VL 和时间衰减。检测器数量不代表已验证的检出率。

- `checks.<id>.enabled`：启用或关闭检测。
- `checks.<id>.observe-only: true`：保留告警和统计，禁止该检测自动踢出与封禁。
- `kick-vl` / `ban-vl`：0 继承全局阈值，-1 关闭对应处罚。
- `punishments.auto-ban-hours`：自动封禁默认 24 小时，0 表示永久。

Aim、AutoClicker、XRay、Backtrack 默认观察。新环境应先收集正常玩法样本，再决定处罚策略。

## 管理命令

| 命令 | 用途 |
|---|---|
| `/kac help` | 查看完整命令 |
| `/kac reload` | 重载配置 |
| `/kac checks`、`/kac toggle <检测器> [on\|off]` | 查看、切换检测器 |
| `/kac info <玩家>`、`/kac stats` | 查看违规记录与统计 |
| `/kac cinfo <玩家>`、`/kac list` | 查看客户端报告概况 |
| `/kac detail <玩家>` | 请求客户端后台扫描详细清单 |
| `/kac modsmd5 <玩家>`、`/kac packsmd5 <玩家>` | 请求文件摘要清单 |
| `/kac reset <玩家>` | 清零 VL |
| `/kac ban <玩家> [小时] [原因]`、`/kac unban <玩家>` | 管理封禁 |
| `/kac kick <玩家> [原因]` | 踢出玩家 |
| `/kac alerts [on\|off]`、`/kac verbose [on\|off]` | 管理提示与日志 |

权限：`kac.admin` 管理，`kac.notify` 告警，`kac.bypass` 全局豁免，`kac.bypass.<检测器>` 单项豁免。
