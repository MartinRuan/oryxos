# Feature Specification: 第18节 CLI 命令行入口与会话持久化管理

**Feature Branch**: `018-lesson18-cli`
**Created**: 2026-08-30
**Status**: Ready for Planning
**Input**: 第18节需求：CLI 命令行入口与会话持久化管理——本地交互、轻重命令分流与多轮会话地基

---

## 背景与价值

OryxOS 作为 Distributed AI Agent OS，在具备 Provider（模型调用）与 ReAct（思考循环）核心引擎后，需要一个方便开发者在终端中与 Agent 交互、启动服务、查看状态与配置的统一命令行入口（CLI）。
CLI 本身是薄薄的接入层，不碰 Agent 内部推理与工具逻辑，只负责读入用户消息、转交统一引擎处理并输出结果。
同时，CLI 是第一个真正“用起来”Session 的入口，因此本节一并交付统一会话持久化基础设施（`SessionManager` 与 SQLite `sessions` 关系表），将会话 ID 拼接规则唯一收敛，为 CLI、Web Service 与后续定时任务打牢共享会话地基。

---

## 用户场景

1. **终端交互对话（chat）**：开发者在终端输入 `oryxos chat`（或指定 `--profile <name>`），进入交互式会话，输入一问一答，底层经 ReAct 引擎处理后打印回复，输入 `/quit` 优雅退出。
2. **轻量运维查询（轻命令）**：开发者执行 `oryxos profile list` 或 `oryxos init` 等命令，无需启动庞大的 Spring 上下文即可秒级返回文件系统目录或完成工程脚手架初始化。
3. **会话幂等与隔离（SessionManager）**：同一用户通过同一渠道（如 CLI）访问同一 Profile 多次获取会话时，系统返回同一个持久化会话实例（对话历史完整累积）；当渠道（channel）、用户（user）或 Profile 任一维度不同时，严格隔离为不同会话。
4. **服务常驻与状态查看（重命令）**：开发者执行 `oryxos serve`、`oryxos gateway` 或 `oryxos status`，引导启动底层 Spring 上下文并正确加载全部模块的 JPA 仓储与实体扫描。

---

## 功能需求 (Functional Requirements)

- **FR-001**: 系统必须提供 Picocli 命令行主入口（`OryxOsCli`），统一管理 12 个子命令：`init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`，并支持标准 `--help` 帮助信息输出。
- **FR-002**: 命令行必须实现**轻重命令分流**机制：
  - **轻命令**（如 `init`、`profile list` 等仅做文件/目录读写操作）：禁止启动 Spring 上下文，直接通过文件系统 API 执行并秒回；
  - **重命令**（如 `chat`、`serve`、`gateway` 等涉及模型调用或引擎调度）：按需引导启动 Spring 上下文。
- **FR-003**: 重命令在引导启动 Spring 上下文时，启动配置必须显式声明 `@EnableJpaRepositories` 与 `@EntityScan` 的 basePackages（覆盖 `com.oryxos` 全局或跨模块包），防止出现多 Maven 模块下 JPA 扫描遗漏问题。
- **FR-004**: 系统必须提供 `CliChannel` 控制台渠道交互实现，负责读 stdin、输出 stdout，维护当前 Session 状态，每行输入交由 `AgentService.process` 执行 ReAct 循环，并在检测到输入为 `/quit` 时跳出循环退出。
- **FR-005**: 系统必须交付统一会话管理接口 `SessionManager` 及基于 SQLite 的 JPA 实体持久化实现：
  - 对外提供 `getOrCreate(channel, user, profileName)`、`get(sessionId)`、`save(session)`、`archive(sessionId)` 等方法；
  - 会话 ID（`sessionId`）生成规则**严格且唯一收敛在 `SessionManager` 内部**（由 channel + user + profile 唯一确定），外部调用方禁止自行拼接字符串。
- **FR-006**: 系统必须交付 `sessions` 关系表与 JPA 实体（`SessionEntity` / `SessionRepository`），字段包含 `session_id`（主键）、`profile_name`、`channel`、`user_id`、`messages_json`、`status`、`created_at`、`last_active_at`、`archived_at`。
- **FR-007**: 会话对话历史序列化为 JSON 字符串保存在 `messages_json` 列中，并在读取时完整反序列化还原全部消息类型（User、Assistant、ToolCall、ToolResult）。
- **FR-008**: 数据库初始化必须提供标准手工建表 SQL 脚本，管理 SQLite DDL，严禁依赖 Hibernate `ddl-auto=update` 自动迁移。

---

## 明确不做（边界）

1. **不做复杂 CLI 富文本渲染 / TUI**：保持纯文本控制台输入输出，不引入 ncurses 等重量级字符终端 UI 框架。
2. **不做 sessions 消息逐条拆表**：核心阶段保持单表单列（`messages_json`）整存整取，不拆子表存储。
3. **不做分布式会话共享 / 远程缓存**：会话在核心阶段仅持久化于单机本地 SQLite。
4. **CLI 不包含任何 Agent 智能决策**：CLI 严格只做消息输入、转交 `AgentService` 与结果打印，禁止在 CLI 内部写死任何模型调用或工具调度。
5. **不实现 Web Service 与 IM 渠道业务逻辑**：`serve` 与 `gateway` 命令仅作为启动骨架，REST 端点与 IM 适配分别留给后续第 26 节与扩展模块。

---

## 验收标准 (Acceptance Criteria)

### 自动化 Harness 验收（`mvn test` 全绿即通过）：
- **`SessionManagerTest`**：
  1. 验证同一三元组（channel, user, profile）历次调用 `getOrCreate` 返回同一个 Session（幂等性，多轮对话历史串联）；
  2. 验证 channel、user 或 profile 任一维度不同时，返回不同的 Session（租户与渠道隔离）；
  3. 验证 `sessionId` 格式生成完全收敛于 `SessionManager` 内部。
- **`SessionRepositoryTest`**：
  1. 验证手工建表脚本创建的 `sessions` 表支持完整 CRUD；
  2. 验证多轮对话消息序列化入库后，重读能够完整还原 `ChatMessage` 列表；
  3. 验证模拟应用重启（新建持久化会话管理器查同一三元组）历史记录不丢失。

### 人工验证清单（见课件“五、做完怎么验”）：
- `oryxos chat` 能进入控制台交互，完成一次多轮对话，输入 `/quit` 正常退出；
- 轻命令（`init`、`profile list`）秒级响应，不启动 Spring Boot；
- 重命令（`chat`）启动日志中 "Found N JPA repository interfaces" 的 N > 0；
- 12 个子命令均能执行，`--help` 格式清晰、参数说明完备。

---

## 依赖与假设

- **前序节交付物依赖**：
  - 第 16 节：`Profile`、`ProfileRegistry`、`ProviderService`、`LlmCallRepository`；
  - 第 17 节：`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`ToolInvocationRepository`。
- **外部库依赖**：Picocli 4.x、Spring Boot 3.x、Spring Data JPA、SQLite JDBC、Jackson。
