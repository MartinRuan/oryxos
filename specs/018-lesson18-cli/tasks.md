# Tasks: 第18节 CLI 命令行入口与会话持久化管理

**Feature**: `018-lesson18-cli`
**Plan**: [plan.md](file:///e:/study/aiprogram/oryxos/specs/018-lesson18-cli/plan.md)
**Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/018-lesson18-cli/spec.md)

---

## Phase 1: Setup (Shared Infrastructure & DDL)

**Purpose**: 确认跨模块依赖与 SQLite 手工建表 DDL

- [X] T001 [P] 确认 `oryxos-cli`、`oryxos-channel-cli`、`oryxos-storage` 与 `oryxos-core` 模块依赖在 `pom.xml`
- [X] T002 [P] 确认并更新 SQLite 手工建表脚本 `schema.sql` 包含 `sessions` 表及索引在 `oryxos-storage/src/main/resources/schema.sql`

---

## Phase 2: Foundational (Session Management & Persistence) 🎯 会话地基

**Purpose**: 交付统一会话管理接口与基于 SQLite + JPA 的持久化层，作为所有触发入口（CLI、Web、定时）的共享地基

**Goal**: 实现 `SessionManager` 接口契约、`SessionEntity` 实体、`SessionRepository` 仓储以及 `JpaSessionManager` 实现，将 `sessionId` 生成规则（channel+user+profile）唯一收敛在内部，支持对话历史 JSON 序列化存储与反序列化回读。

**Independent Test**: 运行 `SessionManagerTest` 与 `SessionRepositoryTest`，验证幂等性、渠道/用户隔离、DDL 建表、消息序列化回读与重启恢复。

### Tests for Foundational Phase
- [X] T003 [P] 编写 `SessionManagerTest.java`（同一三元组历次 `getOrCreate` 返回同一个 Session 幂等性；channel/user/profile 任一不同则是不同 Session；id 生成唯一收敛于内部）在 `oryxos-core/src/test/java/com/oryxos/core/session/SessionManagerTest.java`
- [X] T004 [P] 编写 `SessionRepositoryTest.java`（手工 SQLite 表读写 CRUD；`messages_json` 序列化与反序列化完整性；模拟重启从 DB 重查历史完整）在 `oryxos-storage/src/test/java/com/oryxos/storage/repository/SessionRepositoryTest.java`

### Implementation for Foundational Phase
- [X] T005 [P] 完善 `SessionManager.java` 门面契约（包含 `getOrCreate(String channel, String user, String profileName)`、`get(String sessionId)`、`save(Session session)`、`archive(String sessionId)`）在 `oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java`
- [X] T006 [P] 更新 `InMemorySessionManager.java`（同步支持新的三元组 ID 生成规则）在 `oryxos-core/src/main/java/com/oryxos/core/session/InMemorySessionManager.java`
- [X] T007 [P] 创建 `SessionEntity.java` JPA 持久化实体在 `oryxos-storage/src/main/java/com/oryxos/storage/entity/SessionEntity.java`
- [X] T008 [P] 创建 `SessionRepository.java` 仓储接口在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/SessionRepository.java`
- [X] T009 实现 `JpaSessionManager.java`（基于 JPA 实现 `SessionManager`，处理实体转换与 JSON 序列化，支持通过 `sessions` 表落库）在 `oryxos-storage/src/main/java/com/oryxos/storage/session/JpaSessionManager.java`

---

## Phase 3: User Story 1 - 控制台交互对话（CliChannel & ChatCommand）(Priority: P1) 🎯 MVP

**Goal**: 实现 `CliChannel` 控制台交互读写循环，结合 `ChatCommand` 接收命令行参数并调起 ReAct 循环，支持 `/quit` 退出。

**Independent Test**: 运行 `CliChannelTest`，通过模拟输入流验证多轮问答正确调用 `AgentService.process`、结果输出到 stdout，并在遇到 `/quit` 时正常终止退出。

### Tests for User Story 1
- [X] T010 [P] [US1] 编写 `CliChannelTest.java`（模拟 stdin 输入多轮对话并验证 stdout 输出、校验 `/quit` 优雅退出、校验调用 `AgentService.process`）在 `oryxos-channel-cli/src/test/java/com/oryxos/channel/cli/CliChannelTest.java`

### Implementation for User Story 1
- [X] T011 [US1] 实现 `CliChannel.java`（维护 Session，循环读取 stdin，`/quit` 退出判断，转交 `AgentService.process`，打印回复）在 `oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java`
- [X] T012 [US1] 实现 `ChatCommand.java`（Picocli `@Command(name = "chat")`，支持 `--profile` 选项，启动 Spring 引导 `CliChannel`）在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ChatCommand.java`

---

## Phase 4: User Story 2 - 轻重命令分流与 Picocli 12 个子命令体系 (Priority: P1)

**Goal**: 构建 `OryxOsCli` 主入口，实现轻命令（不启动 Spring 直接操作文件/配置）与重命令（显式声明跨模块包扫描并引导 Spring 上下文）的分流机制，落地全部 12 个子命令。

**Independent Test**: 运行 `OryxOsCliTest`，验证各子命令注册正确、`--help` 格式完整、轻命令（如 `profile list`、`init`）秒级执行且不触发 Spring 容器启动。

### Tests for User Story 2
- [X] T013 [P] [US2] 编写 `OryxOsCliTest.java`（验证 12 个子命令注册与 `--help` 输出，验证轻命令执行不加载 Spring 上下文）在 `oryxos-cli/src/test/java/com/oryxos/cli/OryxOsCliTest.java`

### Implementation for User Story 2
- [X] T014 [P] [US2] 实现 `SpringContextLauncher.java`（显式配置 `@SpringBootApplication(scanBasePackages = "com.oryxos")`、`@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")`、`@EntityScan(basePackages = "com.oryxos.storage.entity")`）在 `oryxos-cli/src/main/java/com/oryxos/cli/launcher/SpringContextLauncher.java`
- [X] T015 [P] [US2] 实现轻命令 `InitCommand.java`（初始化 `.oryxos/` 工作区目录结构）在 `oryxos-cli/src/main/java/com/oryxos/cli/command/InitCommand.java`
- [X] T016 [P] [US2] 实现轻命令 `ProfileCommand.java`（包含 list, create, show, delete 子命令，直接读写 `.oryxos/` 或文件系统）在 `oryxos-cli/src/main/java/com/oryxos/cli/command/ProfileCommand.java`
- [X] T017 [P] [US2] 实现轻命令 `StatusCommand.java`、`ProviderCommand.java`、`ToolCommand.java`、`SessionCommand.java`（列举/查看基础信息）在 `oryxos-cli/src/main/java/com/oryxos/cli/command/`
- [X] T018 [P] [US2] 实现重命令 `ServeCommand.java` 与 `GatewayCommand.java`（常驻服务骨架，引导 Spring）在 `oryxos-cli/src/main/java/com/oryxos/cli/command/`
- [X] T019 [US2] 完善 `OryxOsCli.java` / `OryxCliCommand.java` 主入口（注册 12 个子命令，提供标准 main 函数与帮助输出）在 `oryxos-cli/src/main/java/com/oryxos/cli/OryxOsCli.java`

---

## Phase 5: Polish & Gate Verification

**Purpose**: 执行跨模块集成检查、代码格式化与全面质量门禁

- [X] T020 检查与调整 `oryxos-boot` 启动与自动配置聚合（确保 CLI、Channel、Core、Storage 完整装配）在 `oryxos-boot`
- [X] T021 运行完整 Maven 构建与质量门禁：`mvn clean verify`（通过 Spotless 格式化、P3C 规约、Checkstyle、SpotBugs、OWASP 检查）
