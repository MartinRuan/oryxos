# Feature Specification: US-1 LLM Provider 对接与显式路由 (LLM Provider Integration & Explicit Routing)

**Feature Branch**: `001-llm-provider-integration`

**Created**: 2026-08-29

**Status**: Ready for Planning (Clarified)

**Input**: User description: "US-1: 对接 LLM Provider (ProviderService 门面、provider name 到 ChatModel 显式映射、多模型协议适配、主流云端/本地 Provider 矩阵、Function Calling Schema 契约生成与离线 Mock 支持)"

## Clarifications

### Session 2026-08-29
- Q: 当向外部大模型（云端或本地 Ollama/vLLM）发起推理调用时，ProviderService 层的默认请求超时时间（Timeout）与失败重试策略应如何设定？ → A: 默认 120s 超时；针对底层网络连接异常与服务端 5xx 临时错误开启自动指数退避重试（默认最多重试 2 次），4xx 鉴权/客户端错误不重试直接阻断。
- Q: 当 Agent 请求中仅指定了 Provider 标识但未显式指定具体的模型名称时，系统应如何确定调用的具体模型型号？ → A: 每个 Provider 在配置中显式绑定默认模型（例如 deepseek 默认为 `deepseek-chat`，qwen 默认为 `qwen-plus`），请求未指定 model 时自动回退为该 Provider 预配置的默认模型。
- Q: 在当前 US-1 特性中，ProviderService 门面接口的执行模型是严格仅提供同步阻塞调用，还是需要在此阶段一并实现流式 Token 输出？ → A: 严格遵循技术宪章原则八，当前阶段仅实现纯同步阻塞调用契约，全链路依靠 Java 21 虚拟线程承载并发，流式（SSE）在后续扩展阶段统一引入。
- Q: 为了保障在无外部网络连接、无真实模型 API Key 的 GitHub Actions CI 流水线与本地离线单元测试环境下能够 100% 通过自动化验证，系统是否需要内置支持一个预注册的轻量级 mock Provider？ → A: 内置开箱即用的 `mock` Provider 实现，支持预设纯文本生成与模拟 Function Calling 工具调用返回，使离线测试与 CI 流水线具备零网络依赖与毫秒级执行能力。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 显式多 Provider 注册与按名路由调用 (Priority: P1)

作为 Agent 运行时引擎，我需要根据 Agent Profile 中声明的 `provider.name` 准确路由并调用对应的大模型服务（包括云端模型如 DeepSeek、通义千问 Qwen、Kimi、OpenAI，企业本地部署的 Ollama / vLLM 推理服务，以及用于离线测试的 Mock 模型），以便上层业务 Agent 能够自由切换模型底座而无需改动业务逻辑，且不发生路由歧义。

**Why this priority**: 核心阶段的基础底座，只有大模型能够被准确寻址、支持多厂商且稳定调用，上层 ReAct 循环与 Agent 业务能力才能建立。

**Independent Test**: 配置不同模型提供商标识（`qwen`、`deepseek`、`kimi`、`openai`、`ollama`、`mock`），使用统一服务接口分别发起对话请求，系统能够依据传入的 Provider 名称精准调用目标模型并正确返回生成文本。

**Acceptance Scenarios**:

1. **Given** 系统已配置 `qwen`（通义千问）与 `deepseek` 两个有效 Provider，**When** 调用请求指定 `provider="qwen"` 及提示词 "Hello"，**Then** 系统向通义千问发起调用并返回模型生成的内容。
2. **Given** 系统已配置 `deepseek` 且其默认模型为 `deepseek-chat`，**When** 调用请求指定 `provider="deepseek"` 且省略 `model` 字段，**Then** 系统向 DeepSeek 模型发起调用并默认采用 `deepseek-chat` 模型返回结果。
3. **Given** 企业配置了本地 `ollama`（提供兼容 OpenAI 协议的本地服务），**When** 调用请求指定 `provider="ollama"`，**Then** 请求直达企业内网本地模型并返回结果，数据不出企业内网。
4. **Given** 处于离线单元测试或 CI 环境，**When** 调用请求指定 `provider="mock"`，**Then** 系统通过内置 Mock Provider 立即返回预设结果，零网络依赖。
5. **Given** 系统仅配置了 `qwen`，**When** 调用请求指定未注册的 `provider="unknown-model"`，**Then** 系统应即时拒绝请求并返回明确的"Provider 未注册"错误提示。

---

### User Story 2 - 工具 Function Calling Schema 生成与协议抹平 (Priority: P1)

作为 ReAct 循环引擎，我需要系统能够将给定的工具元数据（工具名称、描述、入参 JSON Schema）无缝转换为对应大模型所需的 Function Calling 格式，并在大模型决定调用工具时解析出规范的工具调用意图（包含工具名与参数 JSON），同时严格禁止底层框架自动执行工具。

**Why this priority**: 工具调用是 ReAct 推理的核心支柱；必须由底层完成各家模型 Function Calling 协议差异的抹平，同时将工具调度的控制权完整保留给 OryxOS 引擎。

**Independent Test**: 传入包含 `read_file` 工具定义的请求至支持 Function Calling 的模型，模型返回结构化的工具调用请求，系统成功解析工具名称与参数，且未发生底层框架自动触发工具执行的行为。

**Acceptance Scenarios**:

1. **Given** 请求中携带了 1 个或多个工具定义（含参数字段描述），**When** 提交给指定模型 Provider，**Then** 系统正确将工具列表转化为该模型支持的 Function Calling 描述体并传给模型。
2. **Given** 模型判定需要调用工具，**When** 模型返回响应，**Then** 系统输出标准化的工具调用指令列表（工具名、入参 JSON 字符串），不触发实际工具代码执行。
3. **Given** 某些模型不支持 Function Calling 或未提供工具列表，**When** 发起普通对话调用，**Then** 系统正常返回纯文本响应。

---

### User Story 3 - 模型参数与消息上下文动态适配 (Priority: P2)

作为 Agent 业务开发者，我希望在发起调用时能够动态传递温度（temperature）、最大生成长度（max_tokens）、系统提示词（system prompt）以及多轮历史消息，以便精确控制模型的生成风格和上下文理解能力。

**Why this priority**: 多轮对话和不同任务（发散创作 vs 确定性代码执行）依赖不同的模型参数与历史上下文。

**Independent Test**: 传入多轮历史消息与特定 temperature 参数调用模型，验证模型回复基于上下文连贯响应，且参数被正确传递。

**Acceptance Scenarios**:

1. **Given** 包含多轮对话历史（用户、助手交替消息）的请求，**When** 提交给指定 Provider，**Then** 模型生成结合了历史上下文的相关响应。
2. **Given** 请求指定 `temperature=0.0`，**When** 连续两次输入相同提示词，**Then** 模型返回具有确定性的结果。

---

### User Story 4 - 每次调用 Token 与耗时审计数据实时采集 (Priority: P3)

作为企业系统审计员与运维人员，我需要系统在每次调用大模型后立即记录本次调用的 Token 消耗量（输入 Token、输出 Token、总 Token）、耗时、使用的模型与 Provider 名称，以便进行成本核算和全链路追踪。

**Why this priority**: 企业级 Agent OS 的非妥协核心能力，必须做到每次调用开销透明、可追溯，不能事后从日志反解析。

**Independent Test**: 发起一次模型调用，调用完成后检查审计记录对象，验证 Token 数量大于 0、耗时大于 0，且 Provider 与模型信息准确无误。

**Acceptance Scenarios**:

1. **Given** 一次成功的模型调用，**When** 调用执行完成并返回结果，**Then** 系统产生包含 `provider`、`model`、`prompt_tokens`、`completion_tokens`、`total_tokens`、`duration_ms` 的审计数据。
2. **Given** 一次因网络异常失败的模型调用，**When** 调用抛出异常，**Then** 系统同样记录该次失败调用的耗时及错误原因。

---

### User Story 5 - 敏感密钥环境变量安全占位解析 (Priority: P4)

作为系统运维管理员，我希望在配置文件中使用 `${ENV_VAR}` 占位符声明模型 API Key，系统在启动装配时自动从操作系统环境变量解析真实凭证，避免明文密钥泄漏。

**Why this priority**: 企业安全合规红线，禁止在配置和代码中明文硬编码密钥。

**Independent Test**: 配置 API Key 为 `${TEST_LLM_KEY}`，在环境变量中注入该值，系统正常完成模型连接与验证；若环境变量未设置且无默认值，系统给出明确配置缺失错误。

**Acceptance Scenarios**:

1. **Given** 配置文件中 API Key 声明为 `${DEEPSEEK_API_KEY}` 且环境变量存在有效密钥，**When** 系统启动初始化，**Then** 成功解析环境变量并建立 Provider 映射。
2. **Given** 配置文件中 API Key 声明为 `${MISSING_KEY}` 且系统未注入该环境变量，**When** 请求调用该 Provider，**Then** 系统捕获认证缺失并抛出清晰的凭证缺失异常。

---

### Edge Cases

- 当远程模型服务返回 429 (Rate Limit / Quota Exceeded) 或网络超时时，系统应如何向调用方反馈？
  - 系统包装为标准的 Provider 通信异常，保留底层错误码与描述，并记录失败审计信息，供上层决定重试或终止。
- 当远程模型服务返回 5xx 错误或底层发生网络连接抖动时，系统如何重试？
  - 系统在单次请求 120s 超时窗口内，采用指数退避算法自动进行最多 2 次重试；若重试全部失败则向上抛出标准异常。
- 当大模型返回格式畸变或非法的 JSON 参数时，系统如何处理？
  - 系统捕获参数反序列化异常并标记为模型输出格式错误，向上层返回清晰的错误诊断信息。
- 当传入的对话消息列表为空或内容全部为空白字符时，系统应如何处理？
  - 系统在校验阶段直接拦截并返回参数错误（Bad Request），不产生外部网络请求。
- 当多线程并发向同一个 Provider 发起请求时，系统能否保证线程安全与映射稳定性？
  - Provider 映射关系在初始化后为不可变/只读并发映射，各请求独立执行，互不干扰。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供统一的 Provider 门面契约接口，采用纯同步阻塞模型（Synchronous Blocking）根据 Provider 名称标识显式寻址并执行推理调用。
- **FR-002**: 系统必须维护显式的 `provider_name -> 模型实例` 映射表，严禁多 Provider 并存时依赖隐式类型扫描。
- **FR-003**: 系统必须支持云端主流模型适配（包括但不限于 DeepSeek、通义千问 Qwen、Kimi、OpenAI）、本地推理服务（Ollama / vLLM）以及内置脱机测试专用的 `mock` Provider。
- **FR-004**: 系统必须支持将上层工具抽象（包含名称、描述、输入参数定义）转换为目标模型支持的 Function Calling Schema。
- **FR-005**: 系统必须支持解析模型返回的 Tool Calls 意图，包括提取工具名称和调用入参 JSON 串。
- **FR-006**: 系统必须显式禁用任何底层框架的自动工具执行拦截，仅由上层引擎控制工具调度与执行。
- **FR-007**: 系统必须支持主流大模型协议的输入输出抹平，将统一消息结构（System / User / Assistant / Tool）转换为对应模型支持的格式。
- **FR-008**: 系统必须支持在每次请求中动态覆盖模型运行时参数（包括模型子型号、temperature、max_tokens 等）；若请求中未指定模型型号，则自动回退至该 Provider 预配置的 `default_model`。
- **FR-009**: 系统必须在每次大模型调用完成后实时收集完整的 Token 用量指标与执行耗时。
- **FR-010**: 系统默认请求超时时间为 120 秒，对网络抖动与 5xx 错误支持最多 2 次指数退避重试；认证与 4xx 错误不重试直接抛出并记录审计。
- **FR-011**: 系统配置文件中所有模型 API Key 和访问凭证必须支持环境变量占位符注入 `${ENV_VAR}`，严禁代码硬编码。
- **FR-012**: 系统必须保证 Provider 映射表与调用适配层在多线程高并发下的线程安全性。

### Key Entities *(include if feature involves data)*

- **ProviderDescriptor**: 模型提供商描述符，包含 provider 唯一名称、类型（云端/本地/Mock）、默认模型名（`default_model`）、支持的模型列表、接入端点（base URL）、认证凭证配置。
- **ToolSchemaDescriptor**: 工具契约描述符，包含工具名称、功能说明、参数 JSON Schema 规范定义。
- **ToolCallIntent**: 工具调用意图，包含工具调用 ID、目标工具名称、模型传入的入参 JSON 字符串。
- **ChatRequest**: 统一模型调用请求对象，包含目标 provider 名称、模型型号（可选，缺省时自动解析为默认模型）、消息上下文列表、工具列表（可选）、生成参数选项（temperature, max_tokens）。
- **ChatResponse**: 统一模型调用响应对象，包含模型生成内容、思考过程（如有）、工具调用意图列表（如有）、结束原因（stop / tool_calls 等）、用量元数据。
- **LlmCallAudit**: 模型调用审计实体，记录调用 ID、会话 ID、provider 名称、模型型号、prompt_tokens、completion_tokens、total_tokens、调用耗时（毫秒）、时间戳及调用状态。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 开发者可以通过统一接口调用任意已注册的 Provider（云端、本地与 Mock），多 Provider 之间的寻址准确率达到 100%。
- **SC-002**: 对支持 Function Calling 的模型，工具 Schema 生成与工具意图解析准确率达到 100%。
- **SC-003**: 100% 的大模型调用（无论成功或失败）均能产生完整的 Token 消耗与耗时审计数据。
- **SC-004**: 系统在并发场景下支持单实例 500+ 并发模型请求路由调度，路由与适配层额外开销低于 2 毫秒。
- **SC-005**: 框架层自动工具调用触发率为 0%（完全由上层引擎主动掌控），避免重复调用。
- **SC-006**: 在无网络与无真实 API Key 的 CI 环境下，通过内置 Mock Provider 执行自动化测试的成功率为 100%，单次模型测试耗时低于 5 毫秒。
- **SC-007**: 代码与配置文件中敏感凭证泄露数为 0，100% 通过密钥注入与安全扫描。

## Assumptions

- 目标部署环境已具备可访问对应大模型 API 的网络连通性或本地推理服务（如 Ollama / vLLM）。
- 上层调用方（如 ReActLoop）负责会话历史长度的截断与控制，Provider 层负责对传入消息进行格式适配与直传。
- 本阶段聚焦于多 Provider 的显式寻址、协议适配与稳定调用，跨 Provider 的故障自动熔断与 Hedging 机制预留给扩展阶段演进。
- 当前 US-1 特性专注于纯同步阻塞调用契约，流式输出（SSE/Streaming）严格推迟至后续扩展阶段。
