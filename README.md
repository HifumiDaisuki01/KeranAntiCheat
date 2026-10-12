# Keran AntiCheat

Minecraft 通用反作弊维护项目：**服务端插件 + 可选客户端模组**。当前维护 Paper / Fabric **1.20.1**，服务端 **3.2.0**，客户端 **2.1.0**。

© Keran Technology Co., Ltd. · [tech.keran.cc](https://tech.keran.cc) · 问题反馈：FAQ@keran.cc

## 下载

[最新 Release：v3.2.0](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/tag/v3.2.0)

| 文件 | 用途 |
|---|---|
| [KeranAntiCheat-3.2.0.jar](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/download/v3.2.0/KeranAntiCheat-3.2.0.jar) | Paper 服务端插件 |
| [KeranClient-2.1.0.jar](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/download/v3.2.0/KeranClient-2.1.0.jar) | Fabric 客户端模组 |
| [config.yml](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/download/v3.2.0/config.yml) | 新版默认配置参考 |

升级已有服务器时保留原配置，由插件迁移新增设置；不要直接用默认配置覆盖自定义设置。

## 环境与安装

- 服务端：Paper 1.20.1，Java 17。
- 客户端：Minecraft 1.20.1，Fabric Loader ≥ 0.14.21，Fabric API，Java 17。

停服并备份 `plugins/KeranAntiCheat/`，移除旧服务端 JAR，将新版放入 `plugins/`，再启动服务器。
使用客户端模组的玩家应移除旧版 KeranClient，将 **2.1.0** 和 Fabric API 放入 `mods/`。严格模式不接受旧版 KeranClient 2.0.0。

## 严格模式：是否必须安装客户端

在 `plugins/KeranAntiCheat/config.yml` 中设置：

```yaml
client:
  strict-mode: true
join:
  wait-seconds: 20
heartbeat:
  enabled: true
  timeout-seconds: 90
```

| 设置 | 行为 |
|---|---|
| `strict-mode: true` | 必须提交本次连接的有效客户端报告；等待期间限制游戏动作，超时断开；启用心跳检查时，心跳超时也会断开 |
| `strict-mode: false` | 允许未安装客户端的玩家；移动、近战等服务端检测继续工作；已安装客户端仍可上报和供管理员查询 |

新安装默认开启。修改后执行 `/kac reload`。从关闭切到开启时，尚未上报的在线玩家获得新的等待期。

旧配置首次升级时从 `join.enabled && join.kick-if-no-client` 迁移；之后 **`client.strict-mode` 为准**。`kac.bypass` / 旧版 `kacc.bypass` 可以豁免客户端准入，管理员身份本身不会豁免。

`strict-md5.enabled` 是独立的文件摘要校验开关，不用于代替上述准入开关。客户端 JAR 或文件路径规则变化后，旧 MD5 基线需要重新登记。

## 3.2.0 更新重点

- 使用会话 challenge/nonce、版本、玩家名和指纹自洽校验；注册频道不再等于安装验证通过。
- 近战伤害关联 Paper 的同 tick、同目标攻击请求，避免把仅归因玩家的插件伤害算作原版近战。
- Reach 使用眼睛到实际碰撞箱最近点；Hitbox 使用正向射线，修复正后方几何盲区。
- 拆分检测器状态，修复目标切换、击退容忍、背包统计、聊天线程隔离及挖掘权重。
- 违规记录保留离线历史，使用主线程快照与后台串行原子写入；重连恢复记录并按离线时间衰减。
- 客户端移除进服主线程等待，后台扫描、游戏线程发送，隔离重连会话；管理员查询按请求 ID 匹配。
- 修复客户端 `fabric.mod.json` 打包，增加 Gradle wrapper、双端 CI 与回归测试。
- 提供通用 `BehaviorContext` 适配接口，不绑定特定枪械插件。

完整内容见 [更新记录](CHANGELOG-3.2.0.md)、[升级与 API 说明](docs/升级说明-3.2.0.md)、[验证记录](docs/验证记录-3.2.0.md)。

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

## 构建与目录

```text
server/       服务端源码、Maven 配置、回归测试和成品
client/       客户端源码、Gradle wrapper 和成品
docs/         默认配置、升级说明、验证记录
.github/      双端构建工作流
```

服务端：`mvn -f server/pom.xml verify`。
客户端：`cd client`，Linux/macOS 执行 `./gradlew build`，Windows 执行 `gradlew.bat build`。

## 验证与能力边界

服务端及客户端构建通过，12 项回归测试通过；真实 Paper 1.20.1 build 196 上六种准入情况及管理员查询回复匹配通过。GitHub Actions 双端构建也通过。

本版是基础维护批次，尚未实现完整原始数据包采集、历史碰撞箱回溯或移动预测。客户端上报是辅助信息，不能证明玩家环境未被修改；完整伪造客户端报告仍属于威胁。

尚未完成图形 Fabric 客户端、真实作弊及完整生产插件组合的实测。自定义近战请求内的特殊伤害、冲刺和重力等玩法，可通过通用 API 登记有限时长和范围的规则。

## 历史版本

1.16.5 已停止维护，源码保留在 [`1.16.5` 分支](https://github.com/HifumiDaisuki01/KeranAntiCheat/tree/1.16.5)，历史成品见 [v1-release1](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/tag/v1-release1)。

Powered by Keran Technology © 2026
