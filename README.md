# Keran AntiCheat

面向 Minecraft **Paper / Fabric 1.20.1** 的反作弊维护项目：服务端插件与可选客户端模组，当前双端统一版本 **3.2.1**。

[下载最新版](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/latest) · [使用指南](docs/guide.md) · [开发说明](docs/development.md) · [更新记录](CHANGELOG.md)

## 下载与安装

| 文件 | 安装位置 |
|---|---|
| [KeranAntiCheat-3.2.1.jar](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/download/v3.2.1/KeranAntiCheat-3.2.1.jar) | Paper 服务端 `plugins/` |
| [KeranClient-3.2.1.jar](https://github.com/HifumiDaisuki01/KeranAntiCheat/releases/download/v3.2.1/KeranClient-3.2.1.jar) | Fabric 客户端 `mods/`，同时安装 Fabric API |
| [默认配置](server/src/main/resources/config.yml) | 配置参考，升级时保留已有自定义配置 |

需要 Java 17。停服、备份插件目录、移除旧 JAR，再替换新版本。客户端需 Fabric Loader ≥ 0.14.21。
成品通过 **GitHub Releases** 分发，仓库只保留源码、构建工具与维护文档。

## 是否强制安装客户端

在 `plugins/KeranAntiCheat/config.yml` 设置：

```yaml
client:
  strict-mode: true
```

- **true**：要求有效客户端报告，等待期超时断开；启用心跳检查时，心跳超时也会断开。
- **false**：允许原版玩家；服务端检测仍继续工作。

修改后执行 `/kac reload`。新安装默认开启；旧配置自动迁移。`strict-md5.enabled` 是独立的文件摘要校验开关。

## 功能与范围

包含移动、基础近战、世界与交互共 28 个检测器，具备证据累积、VL 衰减、管理命令和违规记录持久化。近战关联 Paper 攻击请求；通用适配 API 支持自定义技能与移动规则，不绑定特定枪械插件。

客户端提供模组与文件清单、会话报告及心跳。Aim、AutoClicker、XRay、Backtrack 默认仅观察。
完整数据包与运动预测引擎尚未实现；客户端自报不能证明环境可信。生产整合包仍需实测，验证范围见[验证记录](docs/validation.md)。

## 仓库导航

| 路径 | 内容 |
|---|---|
| `server/` | Maven 服务端源码与回归测试；POM 提供双端唯一版本号 |
| `client/` | Fabric 客户端源码与 Gradle wrapper |
| `docs/guide.md` | 配置、命令、升级与通用适配 API |
| `docs/development.md` | 构建、版本管理与提交身份 |
| `docs/validation.md` | 已完成验证及限制 |
| `.github/workflows/` | 双端 CI 构建 |

© Keran Technology Co., Ltd. · [tech.keran.cc](https://tech.keran.cc) · 问题反馈：FAQ@keran.cc

Powered by Keran Technology © 2026
