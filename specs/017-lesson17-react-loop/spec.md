# Feature Specification: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

**Feature Branch**: `017-lesson17-react-loop`
**Created**: 2026-08-30
**Status**: Draft
**Input**: 第17节需求：ReAct 循环核心引擎与 Agent 上下文编排

---

## User Scenarios & Testing

### User Story 1 - 自实现 ReAct 循环调度与死循环兜底 (Priority: P1) 🎯 MVP

用户或上层系统发起对话请求，Agent 循环自主决策：若单轮直接给出文本回复则立刻收尾返回；若包含工具调用意图，则交由执行器运行工具并将结果作为观察追加回会话，发起下一轮推理；若模型持续陷入工具调用，循环在达到预设最大轮数（默认 10 轮）时安全强制终止并返回明确提示。

**Why this priority**: ReAct 循环是 Agent 的“大脑中枢”，自实现循环并掌握调度、停止判定与死循环保护是系统的核心基础。

**Independent Test**: 运行 `ReActLoopTest`，验证无工具直接返回、单/多轮工具链协同执行回填、达到最大轮数强制截断且每轮消息均累积进 Session。

**Acceptance Scenarios**:
1. **Given** 用户提问不需要工具，**When** 模型返回纯文本响应，**Then** 循环仅执行 1 轮并返回最终文本，会话累积 1 轮模型响应。
2. **Given** 用户提问需要工具，**When** 模型第一轮返回工具调用并在第二轮返回总结，**Then** 循环调度执行工具并将结果回填，共执行 2 轮后返回最终答复。
3. **Given** 模型每一轮均返回工具调用，**When** 循环达到 `max_iterations`（如 10 轮），**Then** 循环强制终止，返回“达到最大轮数，已停止”的提示，且 `ProviderService.chat` 恰好被调用 10 次。

---

### User Story 2 - Prompt 结构化拼装与上下文截断管理 (Priority: P1)

在每一轮向大模型发起调用前，系统将 System Prompt（含角色设定、Bootstrap 启动文件、Skill 简要描述与当前日期时间）、长期记忆、按窗口截断的会话历史（默认保留最近 20 轮）、以及可用工具元数据按严格顺序装配成结构化 Prompt，保证模型认知准确且上下文不超限。

**Why this priority**: 大模型无法自知系统时间与动态上下文，必须通过标准化的 Prompt 组装机制保证角色连贯性与 Token 窗口可控。

**Independent Test**: 运行 `PromptBuilderTest`，验证四大组成部分顺序正确、历史超过 20 轮被准确截断、Prompt 末尾附带当前日期时间。

**Acceptance Scenarios**:
1. **Given** 设定了角色与 Bootstrap 文件的 Agent，**When** 组装 Prompt，**Then** System Prompt 包含角色设定、Bootstrap 内容且末尾附加当前日期时间行。
2. **Given** 会话历史超过 20 轮消息，**When** 组装 Prompt，**Then** 仅保留最近 20 轮历史消息，早期超长消息被截断丢弃。
3. **Given** 配置了可用工具列表，**When** 组装 Prompt，**Then** 携带可用工具的 Function Calling 定义 Schema。

---

### User Story 3 - 统一工具安全执行与 Day-One 审计落库 (Priority: P1)

模型发出的工具调用由唯一的工具执行器接管，严禁框架自动执行。执行器校验工具存在性与沙箱规则，完成调用后将结果封装。无论工具执行成功或抛出异常，均同步将调用详情、参数、耗时、状态（`success`）与错误信息（`error_message`）持久化至 `tool_invocations` 审计表，且业务异常不被静默吞掉。

**Why this priority**: 工具执行是 Agent 对外产生副作用的关键路径，必须集中安全管控且每次执行全链路可追溯可审计。

**Independent Test**: 运行 `ToolExecutorTest` 与 `ToolInvocationRepositoryTest`，验证成功与失败时审计数据均准确落库，异常正确抛出。

**Acceptance Scenarios**:
1. **Given** 模型发起合法工具调用，**When** 工具执行成功，**Then** 返回封装后的结果，并在 `tool_invocations` 记录 `success=true`。
2. **Given** 工具执行过程中抛出异常或工具不存在，**When** 执行器捕获，**Then** 在 `tool_invocations` 记录 `success=false` 与 `error_message`，并向上层返回或重抛错误。

---

### User Story 4 - 统一 Agent 门面编排与 ProfileContext 线程上下文隔离 (Priority: P2)

所有触发源（CLI、Web、定时）统一调用 `AgentService.process`。在处理入口处将当前 Agent 的 Profile 存入 `ProfileContext`（ThreadLocal），执行 ReAct 循环，处理完成后持久化 Session；无论循环正常结束还是发生异常，均在 `finally` 块中严格清理 `ProfileContext`，防止虚拟线程池复用时产生上下文泄漏。

**Why this priority**: 工具在无 Profile 参数的签名下需要获取 Agent 级配置，通过隔离的 ThreadLocal 传递上下文，并由门面统一生命周期治理。

**Independent Test**: 运行 `AgentServiceTest`，验证正常处理与异常处理时 `ProfileContext` 均被彻底清理，Session 得到持久化。

**Acceptance Scenarios**:
1. **Given** 正常会话请求，**When** 门面处理完成，**Then** 返回结果，Session 得到保存，且 `ProfileContext.current()` 为 null。
2. **Given** 循环内部抛出运行时异常，**When** 门面捕获异常上抛，**Then** `finally` 确保 `ProfileContext.current()` 为 null。

---

### User Story 5 - 上下文文件实时加载与无缓存保障 (Priority: P2)

上下文加载器从文件系统读取 Agent 的 Bootstrap 文件与 Skill 描述。每次 Prompt 组装均实时重新读取文件，不建立内存缓存；当 Profile 显式配置的 Skill 文件缺失时立即抛出明确异常，Bootstrap 文件缺失时记录 WARN 日志而不强行中断。

**Why this priority**: 保证开发者或用户修改本地配置文件与 Agent 指令后下一轮立即生效，避免因静态缓存造成行为不一致。

**Independent Test**: 运行 `ContextLoaderTest`，验证文件修改后即时读取、显式 Skill 缺失报错、Bootstrap 缺失发出告警。

**Acceptance Scenarios**:
1. **Given** 本地 Bootstrap 文件被修改，**When** 下一次加载上下文，**Then** 立即返回修改后的新内容。
2. **Given** Profile 显式引用的 Skill 路径不存在，**When** 执行加载，**Then** 抛出明确的异常阻断错误装配。

---

## Edge Cases

- **死循环调用**：模型每轮均调用工具且永不收敛，循环在 `max_iterations` 轮次强行退出，返回达到最大轮数提示，不发生无限递归或栈溢出。
- **超长历史记录**：多轮会话累积数百条消息时，Prompt 构造器按 `max_history_turns` 截断，只传递最近 N 条，保护模型上下文窗口。
- **工具抛出非受检异常**：工具内部发生网络错误或空指针时，ToolExecutor 拦截记录 `success=false` 与堆栈错误信息，不吞掉审计记录。
- **并发与线程复用泄漏**：虚拟线程或线程池复用执行不同 Agent 任务时，`ProfileContext` 在 `finally` 彻底清除，杜绝跨 Agent 串号。

---

## Requirements

### Functional Requirements

- **FR-001**: 系统必须提供自实现的 `ReActLoop` 调度器，支持最大轮数上限（默认 10 轮）与单/多轮工具协同终止判定。
- **FR-002**: 系统必须提供 `PromptBuilder`，按四段式严格顺序组装 Prompt（System Prompt + Memory + 截断历史 + 工具定义），并在 System Prompt 末尾自动注入当前日期时间。
- **FR-003**: 系统必须提供 `ContextLoader`，支持无缓存实时读取 Bootstrap 文件与 Skill 描述，文件修改立即生效。
- **FR-004**: 系统必须提供 `ToolExecutor` 集中执行工具，首行预留沙箱校验位，严禁 Spring AI 自动执行工具。
- **FR-005**: 系统必须在每次工具执行完成（成功或失败）时，实时将调用记录持久化至 SQLite `tool_invocations` 表。
- **FR-006**: 系统必须提供 `AgentService` 门面与 `ProfileContext`（ThreadLocal），保证无论正常还是异常结束均执行 `ProfileContext.clear()`。
- **FR-007**: 数据库层必须提供手写 SQLite DDL 支持 `tool_invocations` 表，字段包括 `id`, `session_id`, `tool_name`, `input_json`, `result_json`, `success`, `error_message`, `duration_ms`, `created_at`。

### Key Entities

- **ToolInvocation**: 映射 `tool_invocations` 审计表，包含执行 ID、会话 ID、工具名、输入入参 JSON、输出结果 JSON、成败标识、错误描述、执行耗时与时间戳。
- **ProfileContext**: 线程隔离的当前运行 Agent Profile 容器，提供静态 `get()`, `set()`, `clear()` 方法。
- **ToolResult**: 工具执行统一输出结果封装，包含文本输出、原始数据与状态标识。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: 5 项验收 Harness（`ReActLoopTest`, `PromptBuilderTest`, `ToolExecutorTest`, `AgentServiceTest`, `ContextLoaderTest`）100% 绿色通过。
- **SC-002**: 死循环场景在第 10 轮精确终止，`ProviderService.chat` 调用次数不多不少恰好 10 次。
- **SC-003**: `mvn clean verify` 全量静态代码检查（Spotless、Alibaba P3C、Checkstyle、SpotBugs + FindSecBugs）0 违规 0 错误。
- **SC-004**: `ProfileContext` 在异常场景下泄漏率为 0%。

---

## Assumptions

- 依赖第 16 节交付的 `Profile`、`ProviderService`、`LlmCall` 及 SQLite JPA 基础设施。
- 核心阶段工具调用按单请求同步顺序执行，不引入异步响应式或并行工具执行。
- 核心阶段上下文截断采用窗口截取最近 N 轮，暂不引入 LLM 自动压缩总结。
