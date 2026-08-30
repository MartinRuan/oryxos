# Feature Specification: 第16节 Agent Provider 与 Profile 对接与显式路由

**Feature Branch**: `016-lesson16-provider`

**Created**: 2026-08-29

**Status**: Ready for Planning (Clarified)

**Input**: User description: "第16节需求：Agent Provider 与 Profile——对接 LLM 与多模型显式路由"

## Clarifications

### Session 2026-08-29
- Q: 当请求指定了全局未注册的 Provider 名称时，系统应如何处理？ → A: 立即抛出 `ProviderNotFoundException`（或 `ProviderException`），不触发任何网络调用，保持快速失败。
- Q: Spring AI 的自动工具执行（Auto Tool Execution）机制在底层如何配置？ → A: 严格显式关闭（`autoExecuteTools=false`），Provider 仅负责将 `OryxTool` 参数 Schema 翻译为 Spring AI 格式，调度和执行权完全由 OryxOS 的 `ReActLoop` 与 `ToolExecutor` 掌控。
- Q: 当启动扫描遇到 YAML 语法错误或非法 Provider 引用的 Profile 时，系统的启动策略是什么？ → A: 记录 WARN/ERROR 级别结构化日志并跳过该文件，绝不阻断系统启动，保障其余合法 Profile 正常加载。
- Q: 当大模型调用发生超时、限流或网络异常失败时，审计如何记录？ → A: 审计切面先向 `llm_calls` 写入一条记录（`success=false`，`error_message` 包含根因，`duration_ms` 记录耗时），然后再将异常完整上抛给上层处理。
- Q: 离线自动化测试与 CI 流水线如何在无外部 API Key 的环境下验证多 Provider 路由与工具 Schema 转换？ → A: 内置脱机 `MockChatModel` 与 Mockito 单元测试，实现 100% 离线、零网络开销的毫秒级测试套件。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 多 Provider 显式映射与按名路由调用 (Priority: P1)

作为 Agent 运行时引擎，我需要根据 Profile 中声明的 `provider.name` 准确路由并调用对应的大模型服务（支持 DeepSeek、通义千问 Qwen、Kimi、OpenAI、本地 Ollama 以及离线测试 Mock 模型），以便上层业务 Agent 能够自由切换模型底座而无需改动业务逻辑，且不发生路由歧义。

**Why this priority**: 核心阶段的基础底座，只有大模型能够被准确寻址、支持多厂商且稳定调用，上层 ReAct 循环与 Agent 业务能力才能建立。

**Independent Test**: 配置不同模型提供商标识（`deepseek`、`qwen`、`mock`），调用 `chat(sessionId, profile, prompt)` 方法，系统能够依据 Profile 指定的 Provider 名称精准调用目标模型并正确返回响应，未注册的 Provider 抛出明确异常。

**Acceptance Scenarios**:

1. **Given** 系统在 `application.yaml` 中配置了 `deepseek` 与 `qwen` 两个 Provider，**When** 调用 `chat` 时传入使用 `deepseek` 的 Profile，**Then** 系统向 DeepSeek 模型发起调用并返回生成内容，通义千问模型未被调用（不串台）。
2. **Given** 系统未注册名为 `unknown-provider` 的模型提供商，**When** 调用请求传入该 Provider 名称，**Then** 系统抛出明确的 `ProviderNotFoundException`。
3. **Given** 处于离线单元测试或 CI 环境，**When** 调用请求指定 `provider="mock"`，**Then** 系统通过 Mock 模型立即返回预设结果，零网络依赖。

---

### User Story 2 - 工具 Function Calling Schema 转换与关闭自动执行 (Priority: P1)

作为 ReAct 循环引擎，我需要系统能够将 `OryxTool` 的参数 Schema 转换为 Spring AI / 大模型所需的 Function Calling 格式（只翻译、不执行），并在向模型发起请求时显式关闭框架层的自动工具执行（`autoExecuteTools=false`），将工具调度的控制权完整交还给 OryxOS 引擎。

**Why this priority**: 工具调用是 ReAct 推理的核心支柱；必须由 Provider 层完成各家模型 Schema 协议差异的抹平，同时严格防止框架层自动执行工具导致工具被重复调用或绕过沙箱。

**Independent Test**: 传入携带 `OryxTool` 工具定义的请求，验证生成的 Spring AI 工具元数据中字段一一对齐，且发送给 `ChatModel` 的请求中 `autoExecuteTools` 为 `false`。

**Acceptance Scenarios**:

1. **Given** 请求中携带了 1 个或多个 `OryxTool` 定义（含参数 JSON Schema 描述），**When** 提交给模型 Provider，**Then** 适配器正确将工具列表转化为 Spring AI 的 Tool Callback / Schema 描述体，且产物中不含任何实际执行逻辑。
2. **Given** 携带工具定义发起调用，**When** 组装底层模型请求，**Then** 请求参数中显式标记关闭自动工具执行（`autoExecuteTools=false`）。

---

### User Story 3 - Profile 配置解析、环境变量解析与校验注册 (Priority: P1)

作为 Agent 系统运维人员与开发者，我需要系统在启动时扫描 `.oryxos/profiles/` 目录下的 YAML 文件，解析为包含全部运行元数据（`name`、`description`、`identity`、`provider`、`tools`、`skills`、`mcp_servers`、`channels`、`notify_channels`、`schedules`、`bootstrap`、`settings`）的 `Profile` 实体，校验 Provider 存在性，并将 `${ENV_VAR}` 占位符解析为实际环境变量，注册至 `ProfileRegistry` 内存索引。

**Why this priority**: Provider 是整个系统里第一个消费 Profile 的模块，Profile 是 Agent 的静态配置定义，必须在启动期完成解析、校验与就绪索引。

**Independent Test**: 启动扫描包含合法 YAML、`${ENV_VAR}` 占位符以及包含不存在 provider 的 YAML，验证合法配置全部字段正确解析并注册，坏文件输出清晰错误日志且不阻断其余配置加载。

**Acceptance Scenarios**:

1. **Given** `.oryxos/profiles/` 下存在合法 Profile YAML（含全字段配置），**When** `ProfileLoader` 执行扫描加载，**Then** 成功解析为 `Profile` 对象并存入 `ProfileRegistry`。
2. **Given** Profile YAML 中包含 `${DEEPSEEK_API_KEY}` 占位符且环境变量已设置，**When** 加载解析，**Then** 占位符成功解析为环境变量真实值。
3. **Given** Profile YAML 引用了全局未配置的 Provider 名称，**When** 加载解析，**Then** 系统给出清晰的 Provider 不存在校验错误，且不影响其他合法 Profile 加载。

---

### User Story 4 - 每次调用 Token 用量、耗时与成败审计实时落库 (Priority: P2)

作为企业系统审计员与运维人员，我需要系统在每次调用大模型后立即记录本次调用的 Session ID、Provider 名称、模型名称、Token 消耗量、耗时以及调用成败与错误信息，同步写入 SQLite `llm_calls` 审计表。

**Why this priority**: 企业级 Agent OS 的非妥协核心能力，必须做到全链路调用可审计、开销透明，且失败事故在数据库中必须留痕。

**Independent Test**: 分别发起成功与抛出异常的 LLM 调用，检查 `llm_calls` 表记录，验证成功调用包含 Token 用量且 `success=true`，失败调用包含错误原因且 `success=false`，异常被继续上抛。

**Acceptance Scenarios**:

1. **Given** 一次成功的模型调用，**When** 调用执行完成并返回结果，**Then** `llm_calls` 表中新增一条记录，`success=true`，`prompt_tokens`、`completion_tokens`、`total_tokens`、`duration_ms` 准确记录。
2. **Given** 一次因网络超时或模型报错导致失败的调用，**When** 调用抛出异常，**Then** 系统在将异常上抛前，先向 `llm_calls` 表写入一条记录，`success=false`，`error_message` 包含异常原因，`duration_ms` 记录已消耗时间。

---

### Edge Cases

- **未注册的 Provider**: 请求指定不存在的 Provider 名称时，系统必须立即抛出 `ProviderNotFoundException`，不进行任何网络调用。
- **坏文件与格式错误的 Profile YAML**: 扫描时遇到 YAML 语法错误或关键字段缺失时，记录 WARN/ERROR 级别结构化日志，跳过该文件，保证系统正常启动并加载其余合法 Profile。
- **环境变量未设置**: 当 `${VAR}` 未在系统环境变量中定义时，若无默认值则保留原占位或给出清晰的缺失报错，严禁静默替换为空字符串导致诡异鉴权错误。
- **网络调用超时与异常**: 底层模型调用发生超时、限流或 5xx 错误时，审计必须先落库 `success=false` 和错误信息，再将异常完整上抛给 ReActLoop 处理。

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 定义 `Profile` 领域模型，包含 `name`、`description`、`identity`、`provider`、`tools`、`skills`、`mcp_servers`、`channels`、`notify_channels`、`schedules`、`bootstrap`、`settings` 等全字段。
- **FR-002**: 系统 MUST 提供 `ProfileLoader`，使用 SnakeYAML 扫描解析 `.oryxos/profiles/*.yaml`（及 `.oryxos/agents/`），支持 `${ENV_VAR}` 环境变量占位解析与基础合法性校验，损坏文件不阻断正常启动。
- **FR-003**: 系统 MUST 提供 `ProfileRegistry` 内存索引，按 `name` 提供 Profile 快速查找与注册能力。
- **FR-004**: 系统 MUST 在 `application.yaml` 中支持 `oryxos.providers` 全局层配置，并在启动时建立 `provider.name -> ChatModel` 的显式映射表，严禁通过容器类型扫描自动匹配。
- **FR-005**: 系统 MUST 提供统一的 `ProviderService`，提供 `chat(sessionId, profile, prompt)`（及 `call(ChatRequest)`）接口，支持按名路由调用。
- **FR-006**: 系统 MUST 提供工具格式适配器（`ToolSchemaAdapter` / `FunctionCallingAdapter`），将 `OryxTool` 的 JSON Schema 转换为 Spring AI 工具定义，且产物仅含 Schema 说明、不含执行逻辑。
- **FR-007**: 系统在调用底层模型时 MUST 显式关闭 Spring AI 的自动工具执行（`autoExecuteTools=false`）。
- **FR-008**: 系统 MUST 定义 `LlmCall`（`LlmCallEntity`）与 `LlmCallRepository`，提供 SQLite 手工建表脚本 `llm_calls`（含 `success` 与 `error_message` 列）。
- **FR-009**: 系统 MUST 在每次模型调用（无论成功或失败）时同步向 `llm_calls` 写入审计记录。
- **FR-010**: 系统 MUST 提供 `@Tag("integration")` 的 `ProviderSmokeIT` 集成冒烟测试，通过环境变量真实 API Key 验证真实连通性。

### Key Entities

- **Profile**: Agent 运行配置与元数据实体，包含身份、提供商、工具、技能、渠道、调度等全部字段。
- **ProviderDescriptor / ProviderProperties**: 全局 Provider 配置描述符，包含 Provider 名称、模型、API Key、Base URL 等。
- **LlmCall / LlmCallEntity**: LLM 调用审计实体，对应 `llm_calls` 表，包含 `sessionId`、`provider`、`model`、Token 用量、耗时、`success`、`errorMessage`、`createdAt`。
- **OryxTool**: OryxOS 统一工具契约接口，提供名称、描述与入参 JSON Schema。

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 单元测试覆盖率 100% 满足验收 Harness：`ProfileLoaderTest`、`ProviderServiceTest`、`ToolSchemaAdapterTest`、`LlmCallRepositoryTest` 全部绿灯通过。
- **SC-002**: Provider 显式路由准确率 100%，多 Provider 并存时 0 串台（Mock 验证目标被调用 1 次，其余为 0 次）。
- **SC-003**: 审计落库完整率 100%，无论是正常响应还是异常抛出，`llm_calls` 均产生对应的审计记录。
- **SC-004**: 敏感凭证防泄漏率 100%，代码库与配置文件中无任何明文 API Key，全部采用环境变量占位。
- **SC-005**: 全量构建门禁 `mvn clean verify` 100% 成功，Checkstyle, Spotless, SpotBugs, P3C, PMD 零违规。

---

## Assumptions

- 运行环境为 JDK 21，启用 Virtual Threads 支持高并发同步阻塞执行。
- 持久化采用 SQLite，数据库文件位于 `.oryxos/oryxos.db`。
- 本地构建与 CI 环境在无外部网络与真实 Key 情况下，通过脱机 Mock 与 Mockito 即可 100% 通过自动化测试。
