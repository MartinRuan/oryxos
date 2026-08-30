# Tasks: 第16节 Agent Provider 与 Profile 对接与显式路由

**Feature**: `016-lesson16-agent-provider`
**Plan**: [plan.md](file:///e:/study/aiprogram/oryxos/specs/016-lesson16-agent-provider/plan.md)
**Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/016-lesson16-agent-provider/spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认并补全各子模块基础依赖与目录结构

- [x] T001 [P] 确认 `oryxos-core`、`oryxos-provider`、`oryxos-storage` 模块依赖配置在 `pom.xml`
- [x] T002 [P] 维护 SQLite 手工建表脚本 `schema.sql`（包含 `llm_calls` 表及 `success`, `error_message` 字段）在 `oryxos-storage/src/main/resources/schema.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心领域模型与持久化基础，阻塞后续用户故事

- [x] T003 [P] 创建/完善 `Profile.java` 领域模型（包含 `name`, `description`, `identity`, `provider`, `tools`, `skills`, `mcp_servers`, `channels`, `notify_channels`, `schedules`, `bootstrap`, `settings` 全字段）在 `oryxos-core/src/main/java/com/oryxos/core/model/Profile.java`
- [x] T004 [P] 确认 `LlmCallEntity.java` 实体（映射 `llm_calls`，含 `success`, `error_message`）在 `oryxos-storage/src/main/java/com/oryxos/storage/entity/LlmCallEntity.java`
- [x] T005 [P] 确认 `LlmCallRepository.java` 接口在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/LlmCallRepository.java`

---

## Phase 3: User Story 1 - 多 Provider 显式映射与按名路由调用 (Priority: P1) 🎯 MVP

**Goal**: 实现多 Provider 显式映射管理与按名路由分发，防止串台，未知名抛明确异常，关自动执行

**Independent Test**: 运行 `ProviderServiceTest`，双 provider 路由不串台，未知名抛异常

### Tests for User Story 1
- [x] T006 [P] [US1] 编写 ProviderService 验收测试 `ProviderServiceTest.java`（按名路由不串台、未知名抛异常、关自动执行、审计落库）在 `oryxos-provider/src/test/java/com/oryxos/provider/ProviderServiceTest.java`
- [x] T007 [P] [US1] 编写脱机 Mock 测试 `MockProviderServiceTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/MockProviderServiceTest.java`
- [x] T008 [P] [US1] 编写真实 Key 集成冒烟测试 `ProviderSmokeIT.java`（打 `@Tag("integration")`）在 `oryxos-provider/src/test/java/com/oryxos/provider/ProviderSmokeIT.java`

### Implementation for User Story 1
- [x] T009 [P] [US1] 维护脱机测试专用的 `MockChatModel.java` 在 `oryxos-provider/src/main/java/com/oryxos/provider/mock/MockChatModel.java`
- [x] T010 [US1] 完善 `ProviderRegistry.java` 显式映射表管理在 `oryxos-provider/src/main/java/com/oryxos/provider/ProviderRegistry.java`
- [x] T011 [US1] 在 `ProviderService.java` 与 `ProviderServiceImpl.java` 中实现 `chat(sessionId, Profile, Prompt)` 门面方法与路由逻辑在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

---

## Phase 4: User Story 2 - 工具 Function Calling Schema 转换与关闭自动执行 (Priority: P1)

**Goal**: 将 OryxTool 转换为 Spring AI 工具定义 Schema（只翻译、不执行），在底层模型调用中显式关闭自动工具执行

**Independent Test**: 运行 `ToolSchemaAdapterTest`，验证 schema 字段一一对齐且产物无执行逻辑

### Tests for User Story 2
- [x] T012 [P] [US2] 编写工具 Schema 适配器验收测试 `ToolSchemaAdapterTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/adapter/ToolSchemaAdapterTest.java`

### Implementation for User Story 2
- [x] T013 [US2] 实现/重构 `ToolSchemaAdapter.java`（仅生成 Schema，不包含任何执行逻辑）在 `oryxos-provider/src/main/java/com/oryxos/provider/adapter/ToolSchemaAdapter.java`
- [x] T014 [US2] 在 `ProviderServiceImpl.java` 调用链路中确保传递 `autoExecuteTools=false` 在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

---

## Phase 5: User Story 3 - Profile 配置解析、环境变量解析与校验注册 (Priority: P1)

**Goal**: 实现 `ProfileLoader` 与 `ProfileRegistry`，支持 SnakeYAML 解析全字段 Profile、`${ENV_VAR}` 占位解析与合法性校验，坏文件容错

**Independent Test**: 运行 `ProfileLoaderTest`，全字段解析通过，坏文件不阻断，环境变量正常替换

### Tests for User Story 3
- [x] T015 [P] [US3] 编写 ProfileLoader 验收测试 `ProfileLoaderTest.java`（全字段解析、未注册 provider 报错、坏文件容错、`${ENV}` 占位解析）在 `oryxos-core/src/test/java/com/oryxos/core/profile/ProfileLoaderTest.java`

### Implementation for User Story 3
- [x] T016 [US3] 实现 `ProfileRegistry.java` 内存索引（按 name 快速查找）在 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileRegistry.java`
- [x] T017 [US3] 实现 `ProfileLoader.java`（基于 SnakeYAML 扫描解析 `.oryxos/profiles/*.yaml`、`${ENV_VAR}` 环境变量解析、Provider 存在性校验、容错处理）在 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileLoader.java`

---

## Phase 6: User Story 4 - 每次调用 Token 用量、耗时与成败审计实时落库 (Priority: P2)

**Goal**: 在每次模型调用（无论成功或失败）时同步将 Token、耗时、成败状态落库 `llm_calls`

**Independent Test**: 运行 `LlmCallRepositoryTest` 验证手工 SQL 建表与 JPA 操作正常；运行审计测试验证调用失败时依然留痕

### Tests for User Story 4
- [x] T018 [P] [US4] 编写 `LlmCallRepositoryTest.java`（执行手工 `schema.sql` 建表，验证持久化与读取，`success`/`error_message` 存在）在 `oryxos-storage/src/test/java/com/oryxos/storage/repository/LlmCallRepositoryTest.java`
- [x] T019 [P] [US4] 编写 LLM 调用审计拦截集成测试 `LlmCallAuditIntegrationTest.java` 在 `oryxos-provider/src/test/java/com/oryxos/provider/audit/LlmCallAuditIntegrationTest.java`

### Implementation for User Story 4
- [x] T020 [US4] 在 `ProviderServiceImpl.java` 中实现调用成功与失败时的审计落库逻辑（捕获异常记 `success=false` + `error_message` 后上抛）在 `oryxos-provider/src/main/java/com/oryxos/provider/impl/ProviderServiceImpl.java`

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 静态代码规约检查、格式化与全模块门禁验证

- [x] T021 [P] 运行 Spotless 格式化 `mvn spotless:apply`
- [x] T022 运行全量门禁检查 `mvn clean verify`（含 Spotless, Alibaba P3C, Checkstyle, SpotBugs, PMD）
- [x] T023 运行 `quickstart.md` 验证场景，确认第 16 节验收 Harness 100% 绿色通过
