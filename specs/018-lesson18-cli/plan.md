# Implementation Plan: 第18节 CLI 命令行入口与会话持久化管理

**Branch**: `018-lesson18-cli` | **Date**: 2026-08-30 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/018-lesson18-cli/spec.md)

**Input**: Feature specification from `/specs/018-lesson18-cli/spec.md`

## Summary

实现 OryxOS 的 Picocli 命令行主入口（`OryxOsCli`）与 12 个子命令体系，建立轻重命令分流执行架构；实现控制台交互渠道（`CliChannel`）；交付统一会话管理接口（`SessionManager`）与基于 SQLite + Spring Data JPA 的会话持久化（`SessionEntity`、`SessionRepository`、`JpaSessionManager` 及建表脚本）。

## Technical Context

- **Language/Version**: Java 21 (LTS) with Virtual Threads enabled
- **Fixed Tech Stack**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **Testing Strategy**: `测试策略按课件"验收 harness"执行：SessionManagerTest、SessionRepositoryTest（覆盖 同一三元组两次 getOrCreate 幂等、channel/user/profile 隔离、id 生成唯一收敛、手工建表脚本、messages_json 序列化回读、模拟重启历史还在），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **Syntax Forbidden**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

- [x] **原则一：自实现 ReAct Loop**（CLI 仅为接入层，不碰推理调度）
- [x] **原则二：Spring AI 职责限定**（严格作为底层协议适配）
- [x] **原则三：Provider 显式映射**（重命令引导时沿用 ProviderRegistry 映射）
- [x] **原则四：一个目录 = 一个 Agent & 渐进式披露**（CLI profile 子命令对接 Agent 目录与 Profile）
- [x] **原则五：审计表 Day One 写入**（会话持久化与审计表保持统一标准）
- [x] **原则六：应用层白名单沙箱**（外部输入与会话严格受控）
- [x] **原则七：同步阻塞执行模型**（`CliChannel` 交互采用同步阻塞 Scanner/PrintWriter + Virtual Thread）

## Project Structure & Module Allocation

### Documentation (this feature)

```text
specs/018-lesson18-cli/
├── plan.md              # 本计划文档
├── research.md          # 架构调研与轻重命令分流
├── data-model.md        # Session 实体与 SQLite 表结构
├── quickstart.md        # 快速验证指南
├── contracts/           # 接口契约
│   └── session-and-cli-contracts.md
└── tasks.md             # 任务拆解文档
```

### Source Code Allocation

- **oryxos-core**:
  - `com.oryxos.core.session.SessionManager` (增强统一契约：`getOrCreate(channel, user, profileName)` 等)
  - `com.oryxos.core.session.InMemorySessionManager`
- **oryxos-storage**:
  - `com.oryxos.storage.entity.SessionEntity`
  - `com.oryxos.storage.repository.SessionRepository`
  - `com.oryxos.storage.session.JpaSessionManager`
  - `src/main/resources/schema.sql` (手工建表 SQL：`sessions` 表)
- **oryxos-channel-cli**:
  - `com.oryxos.channel.cli.CliChannel` (控制台 stdin/stdout 交互循环、/quit 判定、转交 AgentService)
- **oryxos-cli**:
  - `com.oryxos.cli.OryxOsCli` (主入口，挂载 12 个子命令，轻重命令分流)
  - `com.oryxos.cli.command.ChatCommand`
  - `com.oryxos.cli.command.InitCommand`
  - `com.oryxos.cli.command.StatusCommand`
  - `com.oryxos.cli.command.ServeCommand`
  - `com.oryxos.cli.command.GatewayCommand`
  - `com.oryxos.cli.command.ProfileCommand` (subcommands: list, create, show, delete)
  - `com.oryxos.cli.command.ProviderCommand` (subcommand: list)
  - `com.oryxos.cli.command.ToolCommand` (subcommand: list)
  - `com.oryxos.cli.command.SessionCommand` (subcommand: list)
  - `com.oryxos.cli.launcher.SpringContextLauncher` (显式声明 `@EnableJpaRepositories` / `@EntityScan`)

### Tests

- `com.oryxos.core.session.SessionManagerTest` (oryxos-core)
- `com.oryxos.storage.repository.SessionRepositoryTest` (oryxos-storage)
- `com.oryxos.channel.cli.CliChannelTest` (oryxos-channel-cli)
- `com.oryxos.cli.OryxOsCliTest` (oryxos-cli)

## Test Strategy

- **课件验收 Harness 测试**：
  1. `SessionManagerTest`：
     - 同一三元组两次 `getOrCreate` 返回同一个 Session（幂等性）；
     - channel、user、profile 任一不同则是不同 Session（隔离性）；
     - `sessionId` 统一在 `SessionManager` 内部生成。
  2. `SessionRepositoryTest`：
     - 手工建表脚本创建的 `sessions` 表支持正常读写持久化；
     - `messages_json` 序列化回读后消息完整保真；
     - 模拟重启（新建 context 重查）历史依然完好。
- **全量构建门禁**：`mvn clean verify` 全绿（Spotless、P3C、Checkstyle、SpotBugs、OWASP）。
