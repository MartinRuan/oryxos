# Tasks: Lesson 26 - Web Service and Read-Only Admin Console

**Input**: Design documents from `specs/026-lesson26-web-service/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/rest-api.md](contracts/rest-api.md)

**Tests**: 第 26 节课件明确要求 TDD harness。每个故事先写对应测试并确认失败，再写实现；不得删除断言、加 `@Disabled` 或放宽阈值。

**Organization**: 任务按四个用户故事组织；Phase 2 是共享 Web 门面基础，完成后各故事按 P1 → P1 → P2 → P3 推进。

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 让 Web 模块能直接编译其实际使用的内部服务，并锁定课程要求的运行配置。

- [X] T001 在 `oryxos-web/pom.xml` 增加 `oryxos-provider`、`oryxos-memory`、`oryxos-tool` 内部模块依赖，并运行 `./mvnw -pl oryxos-web -am dependency:tree` 确认现有 Spring MVC、Validation、springdoc 版本
- [X] T002 在 `oryxos-boot/src/main/resources/application.yaml` 保持端口 8080 与 `spring.threads.virtual.enabled=true`，并将 Swagger UI 入口统一配置为 `/swagger-ui`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立所有 Agent HTTP 调用共享的统一异常、60 秒同步等待边界和核心阶段 CORS。

**⚠️ CRITICAL**: 本阶段完成前不得实现任何业务端点。

### Tests

- [X] T003 [P] 先在 `oryxos-web/src/test/java/com/oryxos/web/exception/GlobalExceptionHandlerTest.java` 编写 400/404/500/503/504 映射、统一 `ApiResponse` 四字段和 500 不泄露原始异常消息的失败测试
- [X] T004 [P] 先在 `oryxos-web/src/test/java/com/oryxos/web/service/AgentInvocationRunnerTest.java` 编写正常返回、超时取消、执行异常透传和等待线程中断恢复标志的失败测试

### Implementation

- [X] T005 实现 `oryxos-web/src/main/java/com/oryxos/web/exception/AgentInvocationTimeoutException.java` 与 `oryxos-web/src/main/java/com/oryxos/web/service/AgentInvocationRunner.java`，注入 Boot 自动配置的虚拟线程 `applicationTaskExecutor` 并使用阻塞式 `Future.get(60, SECONDS)`
- [X] T006 重构 `oryxos-web/src/main/java/com/oryxos/web/exception/GlobalExceptionHandler.java`，让既有 `ErrorCode` 获得真实 HTTP 状态、504 使用固定协议、500 仅记录内部详情并返回通用消息
- [X] T007 在 `oryxos-web/src/main/java/com/oryxos/web/config/WebConfig.java` 配置核心阶段全开放 CORS，并验证直接复用 Boot `applicationTaskExecutor`，不声明线程池且不引入 WebFlux、Reactor 或 `CompletableFuture`

**Checkpoint**: 统一错误出口与同步超时边界可独立测试。

---

## Phase 3: User Story 1 - 业务系统进行连续 Agent 对话 (Priority: P1) 🎯 MVP

**Goal**: 提供创建、列出、发消息、查详情和归档五项会话能力，消息只进入既有 Agent 引擎一次。

**Independent Test**: 创建 Web 会话并列出摘要，发送消息后读取最近 100 条历史，再归档；验证 32KB、404、排序与只调用一次。

### Tests for User Story 1

- [X] T008 [P] [US1] 先扩展 `oryxos-core/src/test/java/com/oryxos/core/session/SessionManagerTest.java`，为 `list()` 编写空列表、最后活动时间倒序和归档后仍可查询的失败测试
- [X] T009 [P] [US1] 先扩展 `oryxos-storage/src/test/java/com/oryxos/storage/repository/SessionRepositoryTest.java`，为 SQLite 会话列表最后活动时间倒序编写失败测试
- [X] T010 [US1] 先创建 `oryxos-web/src/test/java/com/oryxos/web/controller/SessionApiControllerTest.java`，覆盖五个会话端点、32KB+1 返回 400、Session 不存在/已归档返回 404、最近 100 条历史、集合摘要不含消息正文，以及正常消息请求对 `AgentService.process` 恰调用一次

### Implementation for User Story 1

- [X] T011 [P] [US1] 在 `oryxos-core/src/main/java/com/oryxos/core/session/SessionManager.java` 增加只读 `list()` 契约，并在 `oryxos-core/src/main/java/com/oryxos/core/session/InMemorySessionManager.java` 实现最后活动时间倒序且保留已归档会话
- [X] T012 [P] [US1] 在 `oryxos-storage/src/main/java/com/oryxos/storage/repository/SessionRepository.java` 增加最后活动时间倒序查询，并在 `oryxos-storage/src/main/java/com/oryxos/storage/session/JpaSessionManager.java` 映射实现 `list()`
- [X] T013 [P] [US1] 创建 `oryxos-web/src/main/java/com/oryxos/web/dto/CreateSessionRequest.java`、`MessageRequest.java`、`MessageResponse.java`、`MessageView.java`、`SessionSummary.java` 和 `SessionDetail.java`，落实非空、32KB 和安全响应字段
- [X] T014 [US1] 在 `oryxos-web/src/main/java/com/oryxos/web/dto/SessionDtoMapper.java` 实现会话摘要与详情映射，详情只保留最新 100 条消息且维持原顺序
- [X] T015 [US1] 实现 `oryxos-web/src/main/java/com/oryxos/web/controller/SessionApiController.java` 的五个会话端点，创建时验证 Profile 并固定 channel=`web`，发消息通过 `AgentInvocationRunner` 委托统一 `AgentService.process`
- [X] T016 [US1] 运行 `./mvnw -pl oryxos-web,oryxos-storage -am test`，确认 US1 新增测试和受影响的 Session 回归测试全绿

**Checkpoint**: 连续会话 MVP 可独立运行，方案 A 的第 11 个集合端点已生效。

---

## Phase 4: User Story 2 - 业务系统一次性调用 Agent (Priority: P1)

**Goal**: 按 Agent 名称同步执行单次任务，内部创建临时会话，不向客户端暴露可复用会话标识。

**Independent Test**: 使用已注册 Agent 调用成功；未知 Agent 返回 404；Provider 故障返回 503；超时返回 504。

### Tests for User Story 2

- [X] T017 [US2] 先创建 `oryxos-web/src/test/java/com/oryxos/web/controller/AgentApiControllerTest.java`，覆盖成功回复、临时唯一 Session、未知 Agent 404、Provider 503、60 秒边界 504，并验证仍只调用一次 `AgentService.process`

### Implementation for User Story 2

- [X] T018 [P] [US2] 创建 `oryxos-web/src/main/java/com/oryxos/web/dto/AgentReply.java`，只暴露最终 `reply`，不暴露临时 Session ID
- [X] T019 [US2] 实现 `oryxos-web/src/main/java/com/oryxos/web/controller/AgentApiController.java`，通过 `ProfileRegistry` 解析 Agent、生成一次性 Session，并经 `AgentInvocationRunner` 调用统一引擎
- [X] T020 [US2] 运行 `./mvnw -pl oryxos-web -am test`，确认 US2 与共享超时/异常回归全绿

**Checkpoint**: 一次性调用可独立于会话 API 使用。

---

## Phase 5: User Story 3 - 查询 OryxOS 能力与运行状态 (Priority: P2)

**Goal**: 提供 Profile、Memory、Tool、health、info 五个只读端点，并确保响应不泄露 Provider 凭证。

**Independent Test**: 不连接真实模型启动聚合应用，五个端点可达；Profile/Provider 响应不含 API key 或通知目标。

### Tests for User Story 3

- [X] T021 [P] [US3] 先扩展 `oryxos-memory/src/test/java/com/oryxos/memory/MemoryServiceTest.java`，为 `MemoryService.load()` 复用长期记忆加载策略编写失败测试
- [X] T022 [P] [US3] 先将 `oryxos-web/src/test/java/com/oryxos/web/controller/SystemControllerTest.java` 替换为 `SystemApiControllerTest.java`，覆盖 Provider 可用状态且断言响应序列化不含 `apiKey`
- [X] T023 [US3] 先创建 `oryxos-boot/src/test/java/com/oryxos/boot/WebSmokeIT.java` 并标记 `@Tag("integration")`，用真实上下文覆盖 `/api/v1/health`、`/info`、`/profiles`、`/tools` 的 Bean 装配与 JPA repository 扫描

### Implementation for User Story 3

- [X] T024 [P] [US3] 在 `oryxos-core/src/main/java/com/oryxos/memory/MemoryService.java` 增加只读 `load()` 契约，并在 `oryxos-memory/src/main/java/com/oryxos/memory/impl/MemoryServiceImpl.java` 委托 `LongTermMemory.load()`
- [X] T025 [P] [US3] 创建 `oryxos-web/src/main/java/com/oryxos/web/dto/ProfileSummary.java`、`ToolSummary.java`、`MemoryView.java`、`ProviderView.java` 和 `SystemInfo.java`，显式排除 API key、通知目标与其他敏感配置
- [X] T026 [P] [US3] 实现 `oryxos-web/src/main/java/com/oryxos/web/controller/ProfileApiController.java`、`MemoryApiController.java` 和 `ToolApiController.java`，分别委托既有 Registry/Service 并返回安全只读 DTO
- [X] T027 [US3] 将 `oryxos-web/src/main/java/com/oryxos/web/controller/SystemController.java` 替换为 `SystemApiController.java`，保留 health 并让 info 通过显式 `ProviderRegistry` 返回安全连通状态
- [X] T028 [US3] 显式运行 `./mvnw -pl oryxos-boot -am -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false test`，并运行 `./mvnw -pl oryxos-web,oryxos-memory -am test`，确认 US3 冒烟和单测全绿

**Checkpoint**: 五个只读查询端点可由真实应用上下文提供。

---

## Phase 6: User Story 4 - 使用只读管理平台观察实例 (Priority: P3)

**Goal**: 在 `/admin` 提供五导航静态管理页，读取真实 GET 端点且没有写操作。

**Independent Test**: 页面能加载会话列表、Profile、Tool、Memory、运行状态，错误时显示 `ApiResponse.message`，页面不存在写按钮。

### Tests for User Story 4

- [X] T029 [US4] 先扩展 `oryxos-boot/src/test/java/com/oryxos/boot/WebSmokeIT.java`，验证 `/admin` 静态资源可达、包含五个导航入口、引用五个只读 GET 数据源且不包含新建/编辑/删除/执行控件

### Implementation for User Story 4

- [X] T030 [P] [US4] 在 `oryxos-web/src/main/resources/static/admin/index.html` 与 `styles.css` 实现无需构建工具的响应式只读五导航页面
- [X] T031 [US4] 在 `oryxos-web/src/main/resources/static/admin/app.js` 调用 sessions/profiles/tools/memory/info 五个 GET 端点，渲染空态与真实数据，并在失败时显示统一响应的 `message`
- [X] T032 [US4] 显式运行 `./mvnw -pl oryxos-boot -am -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false test`，确认管理页静态资源与只读约束全绿

**Checkpoint**: 管理平台五个区域可查看真实数据且无写入口。

---

## Phase 7: Polish & Cross-Cutting Verification

**Purpose**: 对 11 端点契约、课程 harness、全局架构不变量和人工真链路做最终收敛。

- [X] T033 [P] 对照 `specs/026-lesson26-web-service/contracts/rest-api.md` 检查六个 Controller 的 OpenAPI 注解、11 个端点、统一 `ApiResponse`、CORS 与 `/swagger-ui`，修正遗漏但不增加范围
- [X] T034 运行 `./mvnw clean verify`，再用 `rg` 检查本节 Web 业务代码不存在 `System.out`、WebFlux/Reactor/`CompletableFuture`、自建线程池、Spring AI 自动 Tool 执行和增强 switch `default ->`，确认所有质量与安全门禁全绿
- [ ] T035 按 `specs/026-lesson26-web-service/quickstart.md` 人工验证 11 个 curl 真链路、CLI Session 共享存储、500 不泄漏、Provider 503、60 秒 504、200 并发、审计落库、管理页五区域与 Swagger UI，并记录可复现结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖。
- **Phase 2**: 依赖 Phase 1；阻塞所有用户故事。
- **US1 / Phase 3**: 依赖 Phase 2，是连续会话 MVP。
- **US2 / Phase 4**: 依赖 Phase 2；复用共享 `AgentInvocationRunner`，可在 US1 之后或并行实现。
- **US3 / Phase 5**: 依赖 Phase 1；为降低集成冲突，建议在 Phase 2 后执行。
- **US4 / Phase 6**: 依赖 US1 的会话列表和 US3 的四个查询端点。
- **Phase 7**: 依赖全部目标故事完成。

### Within Each User Story

- 测试任务必须先提交并观察到因缺少目标行为而失败。
- 接口与 DTO 在 Controller 前完成。
- 共享服务在调用它的端点前完成。
- 每个故事的 checkpoint 测试通过后再进入下一故事。

### Parallel Opportunities

- T003 与 T004 可并行编写。
- T008 与 T009 可并行编写；T011、T012、T013 在测试落地后可并行实现。
- T021、T022 可并行编写；T024、T025、T026 可在对应失败测试就绪后并行实现。
- T030 可与 T029 的测试编写并行准备，但 T031 必须基于最终 HTML 结构接线。

## Parallel Example: User Story 1

```text
Task T008: 扩展内存 SessionManager 列表测试
Task T009: 扩展 SQLite SessionRepository 排序测试
Task T010: 编写 SessionApiController MVC harness

测试确认失败后：
Task T011: 扩展 SessionManager 与内存实现
Task T012: 扩展 Repository 与 JPA 实现
Task T013: 创建会话 REST DTO
```

## Implementation Strategy

### MVP First

1. 完成 Setup 与 Foundational。
2. 完成 US1 的五项会话能力。
3. 单独运行 US1 的 core/storage/web tests，验证方案 A 的集合查询。
4. 继续 US2、US3、US4，保持每个故事可独立验收。

### Delivery Boundary

本清单只生成规格与可执行任务，不开始业务代码。收到用户对本清单的确认后，才进入 `speckit-implement`。

## Notes

- `[P]` 仅标记不修改同一文件且无未完成依赖的任务。
- 本节不新增数据库表、不新增第三方依赖，不实现认证、SSE、WebSocket、限流、RBAC、Agent CRUD、Memory 写入或调度管理。
- 任何认为测试本身错误的情况必须停下报告，禁止弱化 harness。
- 不自动 commit、push 或执行打包发布脚本。
