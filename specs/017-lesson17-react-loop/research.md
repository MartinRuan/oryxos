# Research: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

## 1. 架构原则与设计决策

### 原则一：自实现 ReAct 循环
- **原因**：Spring AI 的 `ChatClient.prompt().call()` 等自带循环为黑盒机制，难以精确掌控何时停、失败审计、上下文截断以及多模型切换。
- **决策**：在 `oryxos-core` 自实现数十行 Java 的 `ReActLoop`，通过 `ProviderService` 调用大模型，通过 `ToolExecutor` 调度执行工具。

### 原则二：Spring AI 职责限定
- **决策**：严格限制 Spring AI 仅用于多协议抹平与 `@Tool` Schema 生成，禁用自动工具执行机制。工具调度由 `ReActLoop` 控制，执行权完全归 `ToolExecutor`。

### 原则五：审计表 Day One 写入
- **决策**：`tool_invocations` 每次执行无论成功还是失败，均必须同步持久化至 SQLite 数据库（记录 `id`, `session_id`, `tool_name`, `input_json`, `result_json`, `success`, `error_message`, `duration_ms`, `created_at`）。

### 原则六：预留沙箱检查位
- **决策**：`ToolExecutor` 在调用工具前预留沙箱/白名单校验位（在第24节完整接入 `Sandbox` 模块）。

---

## 2. 核心模块与包结构设计

| 组件 | 包路径 | 职责 |
| :--- | :--- | :--- |
| `ReActLoop` | `com.oryxos.core.react` | 核心推理调度循环，控制最大轮数、无工具调用直接返回、工具调用回填 |
| `PromptBuilder` | `com.oryxos.core.prompt` | 组装四段式 Prompt（System Prompt + Memory + 截断历史 + 工具定义），注入当前日期时间 |
| `ToolExecutor` | `com.oryxos.core.tool` | 统一工具执行器，沙箱前置、执行工具、审计落库（成败留痕） |
| `AgentService` | `com.oryxos.core.service` | 对外统一处理门面，绑定 `ProfileContext`，运行 `ReActLoop`，保存 Session，finally 清理 |
| `ProfileContext` | `com.oryxos.core.context` | ThreadLocal 上下文容器，支持工具在执行期间读取当前 Agent Profile |
| `ContextLoader` | `com.oryxos.core.context` | 无缓存实时加载 Bootstrap 文件与 Skill 描述 |
| `Session` | `com.oryxos.core.model` | 会话领域模型，管理消息累积与状态 |
| `SessionManager` | `com.oryxos.core.session` | 会话管理接口，定义 save / get / create 契约 |
| `ToolResult` | `com.oryxos.core.model` | 工具执行结果值对象，包含 success, content, errorMessage, retryable |
| `ToolInvocationEntity` | `com.oryxos.storage.entity` | `tool_invocations` JPA 实体 |
| `ToolInvocationRepository` | `com.oryxos.storage.repository` | `tool_invocations` Spring Data JPA 数据访问接口 |

---

## 3. 静态检查与编码规约避坑

- **P3C 长度限制**：方法行数严格控制在 80 行以内，长逻辑拆分为清晰的私有辅助方法。
- **SpotBugs 序列化/内部表示**：领域对象若暴露集合，使用不可变列表或防拷贝，或在 `spotbugs-exclude.xml` 精确排除。
- **日期时间格式**：Prompt 中当前时间使用 `LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))`，格式清晰且无时区歧义。
