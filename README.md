# Keran AntiCheat — 1.16.5 分支

> © Keran Technology Co., Ltd. | 官网 [tech.keran.cc](https://tech.keran.cc) | 问题反馈 FAQ@keran.cc

这是 **Minecraft 1.16.5** 版本的源码分支（对应 Release 标签 `v1-release1`）。

> 主线开发已迁移至 [`main`](../../tree/main) 分支（Minecraft 1.20.1）。

---

## 版本信息

| 组件 | 版本 | 说明 |
|---|---|---|
| KeranAntiCheat | **2.0.0** | 服务端插件（Spigot 1.16.5） |
| KeranClient | **1.1.0** | 客户端模组（Fabric 1.16.5） |

## 环境要求

**服务端**：Spigot / Paper 1.16.5，Java 8+
**客户端**：Fabric Loader + Fabric API，Minecraft 1.16.5，Java 8+

## 目录结构

```
server/                 服务端插件
  ├── src/              源码
  ├── pom.xml           Maven 构建配置
  └── KeranAntiCheat-2.0.0.jar
client/                 客户端模组
  ├── src/              源码
  ├── build.gradle      Fabric Loom 构建配置
  └── KeranClient-1.1.0.jar
docs/                   使用文档
```

## 检测条目（19 项）

| 分类 | 检测器 |
|---|---|
| **移动** | Fly / Speed / Timer / NoFall / Jesus / Spider / Blink / Elytra |
| **战斗** | KillAura / Aim / Reach / Criticals / AutoClicker / Velocity |
| **世界** | XRay / Nuker / Scaffold / FastUse / Chat |

## 功能特性

- 19 项服务端检测器
- 客户端强制安装门槛（未装客户端禁止进入）
- 客户端指纹上报（mods 清单 / 材质包 / JVM 注入检测）
- MD5 严格校验（校验 mods 与材质包目录整体 MD5）
- 心跳检测（客户端 mod 被卸载/掉线告警）
- 黑名单命中处理

## 安装

### 服务端
1. 将 `KeranAntiCheat-2.0.0.jar` 放入 `plugins/` 目录
2. 启动服务器，自动生成 `plugins/KeranAntiCheat/config.yml`
3. 按需修改后 `/kac reload` 热重载

### 客户端
1. 安装 Fabric Loader（1.16.5）
2. 将 `KeranClient-1.1.0.jar` 与 Fabric API 放入 `mods/`

## 已知限制

本版本使用**老版检测引擎**（单次超限即计违规 + 布尔阈值），在枪械 PVP 环境下**误报率较高**，豁免矩阵也未覆盖全部特殊场景。

如需更好的误报控制，请使用 [`main`](../../tree/main) 分支的 **3.0.0** 版本——改用证据累积模型，检测条目从 19 项扩充到 28 项。

---

*Powered by Keran Technology © 2026*
