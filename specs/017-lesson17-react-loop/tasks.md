# Tasks: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

**Feature**: `017-lesson17-react-loop`
**Plan**: [plan.md](file:///e:/study/aiprogram/oryxos/specs/017-lesson17-react-loop/plan.md)
**Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/017-lesson17-react-loop/spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认模块依赖、SQLite 手工建表 DDL 与共享基础设施

- [x] T001 [P] 确认 `oryxos-core` 与 `oryxos-storage` 模块依赖在 `pom.xml`
- [x] T002 [P] 确认 SQLite 手工建表脚本 `schema.sql` 包含 `tool_invocations` 表在 `oryxos-storage/src/main/resources/schema.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心领域模型与会话管理契约，阻塞后续用户故事

- [x] T003 [P] 创建/完善 `Session.java` 领域模型（包含 `id`, `profileName`, `channel`, `userId`, `messages`, `status`, `createdAt`, `lastActiveAt` 及 `append` / `appendToolResult` 方法）在 `oryxos-core/src/main/java/com/oryxos/core/model/Session.java`
- [x] T004 [P] 创建 `ToolResult.java` 值对象（包含 `success`, `content`, `errorMessage`, `retryable` 及工厂方法）在 `oryxos-core/src/main/java/com/oryxos/core/model/ToolResult.java`
- [x] T005 [P] 创建 `SessionManager.java` 门面契约接口在 `oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java`
- [x] T006 [P] 创建 `ProfileContext.java`（ThreadLocal 容器，提供 `set`, `get`, `current`, `clear` 方法）在 `oryxos-core/src/main/java/com/oryxos/core/context/ProfileContext.java`

---

## Phase 3: User Story 1 - 自实现 ReAct 循环调度与死循环兜底 (Priority: P1) 🎯 MVP

**Goal**: 实现自研 ReAct 循环调度引擎，根据 LLM 响应自主决策收尾或工具调用回填，具备最大迭代轮数（默认 10 轮）兜底保护

**Independent Test**: 运行 `ReActLoopTest`，验证无工具一轮结束、有工具执行回填、转满最大轮数强制停（10轮不多不少）且每轮累积回 Session

### Tests for User Story 1
- [x] T007 [P] [US1] 编写 ReAct 循环验收测试 `ReActLoopTest.java`（无工具一轮收尾、有工具执行回填进下一轮、转满最大轮数强制停、每轮响应和工具结果累积进 Session）在 `oryxos-core/src/test/java/com/oryxos/core/react/ReActLoopTest.java`

### Implementation for User Story 1
- [x] T008 [P] [US1] 创建 `ReActLoop.java` 契约接口在 `oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java`
- [x] T009 [US1] 实现 `ReActLoopImpl.java`（自研调度循环，迭代次数控制、Provider 协作、ToolExecutor 调度、Session 消息累积）在 `oryxos-core/src/main/java/com/oryxos/core/react/impl/ReActLoopImpl.java`

---

## Phase 4: User Story 2 - Prompt 结构化拼装与上下文截断管理 (Priority: P1)

**Goal**: 实现四段式 Prompt 组装器，支持 System Prompt 角色设定、Bootstrap、Skill 描述、当前日期时间注入以及历史消息窗口截断（默认 20 轮）

**Independent Test**: 运行 `PromptBuilderTest`，验证四大组成部分顺序、历史截断、System Prompt 末尾附加当前日期时间

### Tests for User Story 2
- [x] T010 [P] [US2] 编写 Prompt 组装器验收测试 `PromptBuilderTest.java`（四部分顺序正确、历史超 20 轮被截断、System Prompt 末尾含当前日期时间）在 `oryxos-core/src/test/java/com/oryxos/core/prompt/PromptBuilderTest.java`

### Implementation for User Story 2
- [x] T011 [P] [US2] 创建 `PromptBuilder.java` 契约接口在 `oryxos-core/src/main/java/com/oryxos/core/prompt/PromptBuilder.java`
- [x] T012 [US2] 实现 `PromptBuilderImpl.java`（按顺序拼接 System Prompt + Bootstrap + Memory + 截断历史 + 工具 Schema，末尾追加格式化日期时间）在 `oryxos-core/src/main/java/com/oryxos/core/prompt/impl/PromptBuilderImpl.java`

---

## Phase 5: User Story 3 - 统一工具安全执行与 Day-One 审计落库 (Priority: P1)

**Goal**: 实现统一工具执行器，集中执行工具调用，预留沙箱检查位，且每次调用成功/失败均同步写入 `tool_invocations`

**Independent Test**: 运行 `ToolExecutorTest` 与 `ToolInvocationRepositoryTest`，验证成功记录 `success=true`，失败记录 `success=false` 与 `error_message`，异常不吞

### Tests for User Story 3
- [x] T013 [P] [US3] 编写 `ToolInvocationRepositoryTest.java`（手工 SQLite 表操作持久化与查询验证）在 `oryxos-storage/src/test/java/com/oryxos/storage/repository/ToolInvocationRepositoryTest.java`
- [x] T014 [P] [US3] 编写工具执行器验收测试 `ToolExecutorTest.java`（成功写审计 `success=true`、失败写 `success=false` 带原因，异常不吞）在 `oryxos-core/src/test/java/com/oryxos/core/tool/ToolExecutorTest.java`

### Implementation for User Story 3
- [x] T015 [P] [US3] 创建 `ToolInvocationEntity.java` 实体在 `oryxos-storage/src/main/java/com/oryxos/storage/entity/ToolInvocationEntity.java`
- [x] T016 [P] [US3] 创建 `ToolInvocationRepository.java` 接口在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/ToolInvocationRepository.java`
- [x] T017 [P] [US3] 创建 `ToolExecutor.java` 契约接口在 `oryxos-core/src/main/java/com/oryxos/core/tool/ToolExecutor.java`
- [x] T018 [US3] 实现 `ToolExecutorImpl.java`（沙箱检查位预留、工具查找执行、成败审计同步落库 `tool_invocations`）在 `oryxos-core/src/main/java/com/oryxos/core/tool/impl/ToolExecutorImpl.java`

---

## Phase 6: User Story 4 - 统一 Agent 门面编排与 ProfileContext 线程上下文隔离 (Priority: P2)

**Goal**: 实现统一入口 `AgentService`，在虚拟线程中安全设置 `ProfileContext`，运行 ReActLoop，持久化 Session，`finally` 中彻底清理 ThreadLocal

**Independent Test**: 运行 `AgentServiceTest`，验证正常处理与抛异常时 `ProfileContext` 均被清掉，会话得到保存

### Tests for User Story 4
- [x] T019 [P] [US4] 编写 Agent 门面验收测试 `AgentServiceTest.java`（处理期间 `ProfileContext` 可取、处理抛异常时 `finally` 也清掉、处理后 Session 保存）在 `oryxos-core/src/test/java/com/oryxos/core/service/AgentServiceTest.java`

### Implementation for User Story 4
- [x] T020 [P] [US4] 创建 `AgentService.java` 契约接口在 `oryxos-core/src/main/java/com/oryxos/core/service/AgentService.java`
- [x] T021 [US4] 实现 `AgentServiceImpl.java`（ProfileContext 设值、ReAct 调度、Session 持久化、finally 清理）在 `oryxos-core/src/main/java/com/oryxos/core/service/impl/AgentServiceImpl.java`

---

## Phase 7: User Story 5 - 上下文文件实时加载与无缓存保障 (Priority: P2)

**Goal**: 实现 `ContextLoader` 实时加载 Bootstrap 文件与 Skill 描述，无缓存，显式引用缺失报错，Bootstrap 缺失告警

**Independent Test**: 运行 `ContextLoaderTest`，验证改文件后下一次 build 立即读到新内容、Skill 缺失报错、Bootstrap 缺失告警

### Tests for User Story 5
- [x] T022 [P] [US5] 编写上下文加载器验收测试 `ContextLoaderTest.java`（改文件立即生效无缓存、Skill 引用缺失报错、Bootstrap 缺失 WARN）在 `oryxos-core/src/test/java/com/oryxos/core/context/ContextLoaderTest.java`

### Implementation for User Story 5
- [x] T023 [P] [US5] 创建 `ContextLoader.java` 契约接口在 `oryxos-core/src/main/java/com/oryxos/core/context/ContextLoader.java`
- [x] T024 [US5] 实现 `ContextLoaderImpl.java`（按 Profile 读取 Bootstrap、读取 Skill 描述、无缓存、错误告警处理）在 `oryxos-core/src/main/java/com/oryxos/core/context/impl/ContextLoaderImpl.java`

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 静态代码质量检查、格式化与全模块门禁验证

- [x] T025 [P] 运行 Spotless 格式化 `mvn spotless:apply`
- [x] T026 运行全量门禁检查 `mvn clean verify`（含 Spotless, Alibaba P3C, Checkstyle, SpotBugs, PMD）
- [x] T027 运行 `quickstart.md` 验证场景，确认第 17 节验收 Harness 100% 绿色通过
