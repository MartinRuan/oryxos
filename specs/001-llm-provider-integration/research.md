# Phase 0 Research: US-1 LLM Provider 对接与显式路由

**Feature**: `001-llm-provider-integration`
**Date**: 2026-08-29

---

## 1. 关键架构与技术决策

### 决策 1: Provider 显式映射与注册管理机制

- **Decision**: 在 `oryxos-provider` 模块中实现 `ProviderRegistry` / `ProviderService`，维护显式的 `Map<String, ChatModel>` 映射表。系统启动时基于配置或工厂按 `provider.name` 实例化并绑定底层 `ChatModel`；上层通过 `ProviderService.getChatModel(providerName)` 显式检索。
- **Rationale**: 技术宪章原则三明确禁止通过扫描 Spring 容器中 `ChatModel` 的 Bean 类型来区分 Provider（因为各厂商实现类均实现相同的 Spring AI 接口）。显式映射消除了多 Provider 并存时的路由歧义。
- **Alternatives considered**:
  - *按 Spring Bean 名称注入（如 `@Qualifier("deepseek")`）*：当 Provider 是动态由 Agent YAML 配置指定且数量不固定时，静态 `@Qualifier` 无法支持运行时动态路由。
  - *按类型扫描自动推断*：多个 Provider 的 Bean 类型完全一致，无法推断，直接违反宪章。

---

### 决策 2: Spring AI 职责收敛与 Function Calling 协议适配

- **Decision**: 仅使用 Spring AI 的 `ChatModel.call(Prompt)` 协议抹平和 `@Tool` / JSON Schema 描述生成。在构造 `Prompt` 时将 `OryxTool` 的入参描述转为 Spring AI 的 Function Calling 参数，显式将底层自动执行拦截器配置为禁用（通过原生 `ChatModel` 调用而非 `ChatClient` 自动工具执行链）。
- **Rationale**: 技术宪章原则二与需求文档决策二：Spring AI 在 OryxOS 中只做协议转换与 Schema 生成，严禁委托自动执行工具，工具执行必须由后续的 `ReActLoop` 与 `ToolExecutor` 统一接管，杜绝工具被重复调用的严重缺陷。
- **Alternatives considered**:
  - *使用 Spring AI `ChatClient.prompt().tools(...).call()`*：Spring AI 会自动在底层执行工具并进行循环回调，破坏了 OryxOS 对 ReAct 循环、沙箱检查和审计的控制权。
  - *手写各家大模型 HTTP 协议*：重新造轮子，维护成本极高，无法复用 Spring AI 社区对最新主流模型的协议适配成果。

---

###决策 3: 主流云端与企业本地模型（Ollama/vLLM）统一适配方案

- **Decision**:
  1. **通义千问 (Qwen)**：通过 Spring AI Alibaba `DashScopeChatModel` 接入。
  2. **OpenAI / DeepSeek / Kimi / 智谱等兼容协议模型**：通过标准 OpenAI 协议（配置对应的 `base-url` 与 `api-key`）适配。
  3. **本地模型 (Ollama / vLLM)**：通过 OpenAI 兼容端点（如 `http://localhost:11434/v1`）无缝对接，支持企业内网完全离线部署。
  4. **离线测试 (Mock Provider)**：内置 `MockChatModel` / `MockProvider`，支持无网络、零 API Key 状态下毫秒级响应预设纯文本或 Tool Calls。
- **Rationale**: 统一为标准模型抽象，上层 Agent 仅需在 `AGENT.md` 中声明 `provider.name` 与 `model`，无需修改任何代码。
- **Alternatives considered**:
  - *为每家模型单独开发自定义 HTTP 客户端*：适配工作量大，维护代价高。

---

### 决策 4: 请求超时与重试容错机制

- **Decision**:
  1. 单次请求默认超时设为 **120 秒**。
  2. 针对网络层连接中断、连接超时（`ConnectTimeoutException`）以及服务端 5xx 临时故障，采用指数退避算法自动进行最多 **2 次重试**。
  3. 针对客户端 4xx 错误（如 401 鉴权失败、400 参数格式错误），不进行重试，立即向上层抛出标准 `ProviderException`。
- **Rationale**: 大模型生成受上下文长度影响显著，120s 兼顾本地模型与长文本生成；有限的指数重试提升了在弱网与服务偶发抖动时的稳定性，同时避免了非幂等操作的无休止重试。
- **Alternatives considered**:
  - *无任何重试*：轻微网络抖动即导致整个 Agent ReAct 任务失败。
  - *激进重试（如 5 次以上）*：可能导致企业 API 费用激增或线程被长时间占用。

---

### 决策 5: Day One 审计数据采集与 MDC 全链路追踪

- **Decision**: 每次大模型调用封装在 `ProviderService.call(ChatRequest)` 内部切面/模板方法中：
  1. 在调用前记录起始时间戳，从 MDC 获取当前 `traceId` / `sessionId`。
  2. 调用完成后提取 `Usage`（`promptTokens`、`completionTokens`、`totalTokens`）及计算总耗时（`durationMs`）。
  3. 组装 `LlmCallAudit` 审计记录实体，调用 `LlmCallRepository`（或审计监听器）同步落库 SQLite `llm_calls` 表。
  4. 无论调用成功或抛出异常，`finally` 块确保耗时与状态均被记录。
- **Rationale**: 技术宪章原则六要求审计数据必须 Day One 写入，严禁仅打印日志。
- **Alternatives considered**:
  - *事后解析 Logstash JSON 日志*：日志易丢失、解析开销大，无法支撑实时成本管控。
