# Tasks: US-1 LLM Provider 对接与显式路由

**Feature**: `001-llm-provider-integration`
**Plan**: [plan.md](file:///e:/study/aiprogram/oryxos/specs/001-llm-provider-integration/plan.md)
**Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/001-llm-provider-integration/spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 初始化 Provider 模块所需的基础目录与依赖结构

- [x] T001 [P] 确认并补全 `oryxos-provider` 模块的 POM 依赖配置（引入 `oryxos-core` 与 Spring AI 依赖）在 `oryxos-provider/pom.xml`
- [x] T002 [P] 创建 Provider 核心包目录结构在 `oryxos-provider/src/main/java/com/oryxos/provider/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心领域模型与异常契约，必须在所有用户故事开发前就绪

**⚠️ CRITICAL**: 本阶段模型为所有 User Story 的基础依赖，完成后方可进入 User Story 开发

- [x] T003 [P] 创建消息类型枚举 `MessageType.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/MessageType.java`
- [x] T004 [P] 创建对话消息实体 `ChatMessage.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ChatMessage.java`
- [x] T005 [P] 创建工具定义实体 `ToolDefinition.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ToolDefinition.java`
- [x] T006 [P] 创建工具调用意图实体 `ToolCallIntent.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ToolCallIntent.java`
- [x] T007 [P] 创建 Token 用量统计实体 `TokenUsage.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/TokenUsage.java`
- [x] T008 [P] 创建模型结束原因枚举 `FinishReason.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/FinishReason.java`
- [x] T009 [P] 创建 Provider 描述符 `ProviderDescriptor.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ProviderDescriptor.java`
- [x] T010 [P] 创建统一对话请求对象 `ChatRequest.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ChatRequest.java`
- [x] T011 [P] 创建统一对话响应对象 `ChatResponse.java` 在 `oryxos-core/src/main/java/com/oryxos/core/model/ChatResponse.java`
- [x] T012 [P] 创建 Provider 错误码枚举 `ProviderErrorCode.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/exception/ProviderErrorCode.java`
- [x] T013 [P] 创建 Provider 统一异常类 `ProviderException.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/exception/ProviderException.java`
- [x] T014 定义统一 Provider 门面契约接口 `ProviderService.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/ProviderService.java`

**Checkpoint**: 基础模型与接口契约就绪，进入 User Story 研发阶段

---

## Phase 3: User Story 1 - 显式多 Provider 注册与按名路由调用 (Priority: P1) 🎯 MVP

**Goal**: 实现多 Provider 的显式注册表、`ChatModel` 实例映射与统一寻址调用，提供离线测试专用的 `mock` Provider

**Independent Test**: 分别向已注册的 `qwen`、`deepseek`、`mock` 发起调用，验证按名路由准确率 100%，未注册 Provider 准确抛出 `PROVIDER_NOT_FOUND`

### Tests for User Story 1

- [x] T015 [P] [US1] 编写 Provider 显式寻址与按名路由单元测试 `ProviderRegistryTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/ProviderRegistryTest.java`
- [x] T016 [P] [US1] 编写 Mock Provider 离线调用与超时重试测试 `MockProviderServiceTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/MockProviderServiceTest.java`

### Implementation for User Story 1

- [x] T017 [P] [US1] 实现脱机单元测试专用的 `MockChatModel.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/mock/MockChatModel.java`
- [x] T018 [US1] 实现 Provider 显式映射注册中心 `ProviderRegistry.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/ProviderRegistry.java`
- [x] T019 [US1] 实现 ChatModel 工厂类 `ChatModelFactory.java`（支持通义千问、OpenAI/DeepSeek/Kimi/本地 Ollama 与 Mock）在 `oryxos-provider/src/main/java/com/oryxos/provider/adapter/ChatModelFactory.java`
- [x] T020 [US1] 实现统一门面服务 `ProviderServiceImpl.java` 核心路由分发与 120s 超时退避重试逻辑在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`
- [x] T021 [US1] 编写 Provider 自动装配配置类 `ProviderAutoConfiguration.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/config/ProviderAutoConfiguration.java`

**Checkpoint**: User Story 1 (MVP) 可独立运行并脱机验证多 Provider 显式路由

---

## Phase 4: User Story 2 - 工具 Function Calling Schema 生成与协议抹平 (Priority: P1)

**Goal**: 将工具抽象转换为标准 Function Calling Schema 注入模型，并在模型决定调用工具时解析出 `ToolCallIntent`，严禁框架底层自动执行

**Independent Test**: 发起携带 `ToolDefinition` 的调用，验证生成标准 Schema，响应中成功提取 `ToolCallIntent` 列表，且无工具被自动执行

### Tests for User Story 2

- [x] T022 [P] [US2] 编写 Function Calling Schema 转换与意图解析单元测试 `FunctionCallingAdapterTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/adapter/FunctionCallingAdapterTest.java`

### Implementation for User Story 2

- [x] T023 [US2] 实现 Function Calling 适配器 `FunctionCallingAdapter.java`（负责 `ToolDefinition` 转 Spring AI ToolCallback / Schema 定义）在 `oryxos-provider/src/main/java/com/oryxos/provider/adapter/FunctionCallingAdapter.java`
- [x] T024 [US2] 在 `ProviderServiceImpl.java` 中集成 Function Calling 意图提取与参数反序列化，确保原生 `ChatModel.call` 不触发自动执行在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

**Checkpoint**: User Story 2 完成，支持双向抹平各大模型的 Function Calling 协议差异

---

## Phase 5: User Story 3 - 模型参数与消息上下文动态适配 (Priority: P2)

**Goal**: 支持请求动态覆盖 `temperature`、`max_tokens`、多轮历史消息，未传 `model` 时自动回退为 Provider 的 `default_model`

**Independent Test**: 传入多轮历史消息及 `temperature=0.0`，验证参数正确注入模型，缺省 `model` 时准确采用默认模型

### Tests for User Story 3

- [x] T025 [P] [US3] 编写模型参数覆盖与默认模型回退测试 `ModelOptionsAdapterTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/adapter/ModelOptionsAdapterTest.java`

### Implementation for User Story 3

- [x] T026 [US3] 实现多轮消息转换与运行时参数构造器 `PromptAdapter.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/adapter/PromptAdapter.java`
- [x] T027 [US3] 在 `ProviderServiceImpl.java` 中实现缺省模型回退至 Provider 的 `default_model` 逻辑在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

**Checkpoint**: User Story 3 完成，模型运行时配置与上下文完整适配

---

## Phase 6: User Story 4 - 每次调用 Token 与耗时审计数据实时采集 (Priority: P3)

**Goal**: 每次模型调用无论成功或失败均记录 `prompt_tokens`、`completion_tokens`、`total_tokens` 与耗时，同步写入 SQLite `llm_calls` 审计表

**Independent Test**: 发起模型调用，调用后立即查询 `LlmCallRepository`，验证产生与本次调用匹配的审计记录

### Tests for User Story 4

- [x] T028 [P] [US4] 编写 LLM 调用审计记录持久化集成测试 `LlmCallAuditIntegrationTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/audit/LlmCallAuditIntegrationTest.java`

### Implementation for User Story 4

- [x] T029 [P] [US4] 在 `oryxos-storage` 模块创建审计实体 `LlmCallEntity.java` 在 `oryxos-storage/src/main/java/com/oryxos/storage/entity/LlmCallEntity.java`
- [x] T030 [P] [US4] 在 `oryxos-storage` 模块创建持久化接口 `LlmCallRepository.java` 在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/LlmCallRepository.java`
- [x] T031 [US4] 在 `ProviderServiceImpl.java` 中集成审计切面记录逻辑（提取 Usage、计算耗时、落库 SQLite）在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

**Checkpoint**: User Story 4 完成，全链路 Token 与耗时审计 Day One 同步落库

---

## Phase 7: User Story 5 - 敏感密钥环境变量安全占位解析 (Priority: P4)

**Goal**: 支持在配置文件中以 `${ENV_VAR}` 声明 API Key，系统启动时安全解析并校验凭证

**Independent Test**: 配置 `${TEST_KEY}` 占位并在环境中注入，验证成功读取；缺失且无默认值时给出清晰错误提示

### Tests for User Story 5

- [x] T032 [P] [US5] 编写环境变量解析与缺失校验测试 `ProviderPropertiesTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/config/ProviderPropertiesTest.java`

### Implementation for User Story 5

- [x] T033 [US5] 实现 Provider 配置属性类 `ProviderProperties.java`（支持多 Provider 列表与 `${ENV_VAR}` 注入）在 `oryxos-provider/src/main/java/com/oryxos/provider/config/ProviderProperties.java`
- [x] T034 [US5] 在 `application.yaml` 与 `application-dev.yaml` 中配置各 Provider 样例与环境变量占位在 `oryxos-boot/src/main/resources/application.yaml`

**Checkpoint**: User Story 5 完成，满足企业安全合规与敏感凭证防泄漏标准

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 代码规约门禁检查、全量模块验证与回归测试

- [x] T035 [P] 运行 Spotless 格式化并修复 Java 代码风格 `mvn spotless:apply`
- [x] T036 运行全量多模块质量与安全门禁 `mvn clean verify`（含 Spotless, Alibaba P3C, Checkstyle, SpotBugs, OWASP）
- [x] T037 运行 `quickstart.md` 验证场景确保端到端测试 100% 绿色通过

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Setup 完成，**阻塞所有 User Story**
- **User Story 1 (Phase 3)**: 依赖 Foundational 完成（MVP 核心）
- **User Story 2 (Phase 4)**: 依赖 US1 完成
- **User Story 3 (Phase 5)**: 依赖 US1 完成（可与 US2 并行）
- **User Story 4 (Phase 6)**: 依赖 US1 完成（可与 US2/US3 并行）
- **User Story 5 (Phase 7)**: 依赖 US1 完成
- **Polish (Phase 8)**: 依赖所有 User Story 完成

### Parallel Opportunities

- **Foundational 阶段**: T003 ~ T013 均为独立实体/枚举/异常定义，可全并行
- **User Story 阶段**:
  - US2 (Function Calling) 与 US3 (参数/消息适配) 可并行
  - US4 (持久化与审计) 与 US5 (配置与安全) 可并行
- **测试阶段**: 所有 `[P]` 标记的单元测试可并行开发

### Implementation Summary
- 所有 37 项任务（T001~T037）已 100% 实现并全部通过验收测试与质量安全门禁！
