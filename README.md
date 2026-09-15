# Keran AntiCheat

> © Keran Technology Co., Ltd. | 官网 [tech.keran.cc](https://tech.keran.cc) | 问题反馈 FAQ@keran.cc

Minecraft 服务器反作弊体系。**服务端插件 + 客户端模组**双端配合，覆盖移动、战斗、世界三大类共 **28 项检测**。

---

## 版本说明

本仓库同时维护两个 Minecraft 版本，**共用同一仓库**，通过分支与 Release 区分：

| MC 版本 | 分支 | Release 标签 | 服务端插件 | 客户端模组 |
|---|---|---|---|---|
| **1.20.1**（当前主线） | `main` | `v2` | KeranAntiCheat 3.0.0 | KeranClient 2.0.0 |
| **1.16.5**（旧版） | `1.16.5` | `v1-release1` | KeranAntiCheat 2.0.0 | KeranClient 1.1.0 |

### 下载成品

前往 [Releases](../../releases) 页面直接下载对应版本的 jar 包：

- **v2** — Minecraft 1.20.1（Paper 服务端 + Fabric 客户端）
- **v1-release1** — Minecraft 1.16.5（Spigot 服务端 + Fabric 客户端）

---

## 当前主线：1.20.1 (v2)

### 环境要求

**服务端**
- Paper 1.20.1
- Java 17+

**客户端**
- Fabric Loader ≥ 0.14.21
- Fabric API（必需）
- Minecraft 1.20.1
- Java 17+

### 目录结构（main 分支）

```
server/                 服务端插件
  ├── src/              源码 (41 个 java 文件)
  ├── pom.xml           Maven 构建配置
  └── KeranAntiCheat-3.0.0.jar
client/                 客户端模组
  ├── src/              源码 (11 个 java 文件)
  ├── build.gradle      Fabric Loom 构建配置
  └── KeranClient-2.0.0.jar
docs/
  ├── config.yml        服务端配置模板
  └── 使用文档-1.20.1.md
```

### 检测条目（28 项）

| 分类 | 检测器 |
|---|---|
| **移动 (11)** | Fly / Speed / Timer / NoFall / Jesus / Spider / Blink / Elytra / NoSlow / Step / GroundSpoof |
| **战斗 (10)** | KillAura / Aim / Reach / Criticals / AutoClicker / Velocity / AntiKb / Hitbox / Backtrack / AutoBlock |
| **世界 (7)** | XRay / Nuker / Scaffold / FastUse / FastBreak / Chat / Inventory |

### v3.0 引擎：证据累积模型

v2.0 老引擎误报严重的根因是**五重缺陷叠加**，v3.0 逐一修复：

| 老引擎缺陷 | 后果 | v3.0 方案 |
|---|---|---|
| 超阈值一次即计违规 | 网络抖动直接触发 | **连续帧验证** `min-consecutive` |
| 无置信度概念 | 一次异常 = 十次异常 | **证据累积** `alert-threshold` |
| 违规值只增不减 | 误报永久累积致误封 | **VL 时间衰减** `vl-decay-per-10s` |
| 无延迟补偿 | 高 Ping 玩家被误判 | **延迟补偿** `lag-compensation-ratio` |
| 豁免矩阵残缺 | 特殊场景必误报 | 补齐 1.20.1 全部机制豁免 |

**调参原则**：检测误报时，**优先调大 `min-consecutive` 与 `alert-threshold`，而不是直接关闭检测**。

### 核心算法改进

- **自瞄检测**：从"比对 tick 旋转角度"改为 **GCD 灵敏度网格分析 + 旋转平滑度分析**
- **连点器检测**：从"数 CPS"改为 **点击间隔变异系数分析**（人类 CV > 0.15，连点器 < 0.045）
- **距离检测**：延迟 + 目标移动 + 武器附加射程三重补偿
- **透视检测**：新增"未经石层直取珍贵矿物"强特征

完整说明见 `docs/使用文档-1.20.1.md`。

---

## 旧版：1.16.5 (v1-release1)

切换到 [`1.16.5`](../../tree/1.16.5) 分支查看源码。

| 组件 | 版本 | 环境 |
|---|---|---|
| KeranAntiCheat | 2.0.0 | Spigot 1.16.5 / Java 8+ |
| KeranClient | 1.1.0 | Fabric 1.16.5 / Java 8+ |

包含 **19 项检测**，为最初的整合版本。

---

## 安装

### 服务端
1. 将 `KeranAntiCheat-*.jar` 放入服务端 `plugins/` 目录
2. 启动服务器，插件自动生成 `plugins/KeranAntiCheat/config.yml`
3. 按需修改配置后执行 `/kac reload` 热重载

### 客户端
1. 安装对应版本的 Fabric Loader
2. 将 `KeranClient-*.jar` 与 **Fabric API** 一同放入 `mods/` 目录
3. 启动游戏，进服后自动上报指纹

> **客户端模组强制开启**，玩家无法关闭——这是防作弊的前提。

---

## 命令

```
/kac help                      帮助
/kac checks                    查看检测器状态
/kac toggle <检测器> [on|off]  运行时开关检测器
/kac stats                     全局违规统计
/kac info <玩家>               查看违规记录
/kac cinfo <玩家>              查看客户端指纹
/kac detail <玩家>             客户端详细 mods/材质包清单
/kac modsmd5 <玩家>            客户端 mods 目录 MD5
/kac packsmd5 <玩家>           客户端材质包目录 MD5
/kac list                      已上报客户端玩家列表
/kac reset <玩家>              清零违规记录
/kac ban <玩家> [小时] [原因]  封禁（0 小时 = 永久）
/kac unban <玩家>              解封
/kac kick <玩家> [原因]        踢出
/kac alerts [on|off]           切换告警接收
/kac verbose [on|off]          详细日志
/kac reload                    重载配置
```

### 权限

| 权限 | 说明 |
|---|---|
| `kac.admin` | 管理权限（全部命令） |
| `kac.notify` | 接收告警 |
| `kac.bypass` | 豁免全部检测 |
| `kac.bypass.<检测器>` | 豁免单个检测器 |

---

## 声明

- 本仓库为**公开发布仓库**，供服务器服主下载成品使用
- 版权归 Keran Technology Co., Ltd. 所有
- 问题反馈：FAQ@keran.cc

*Powered by Keran Technology © 2026*
