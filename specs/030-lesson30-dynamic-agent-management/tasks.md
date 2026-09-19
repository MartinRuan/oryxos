---

description: "第30节动态管理 Agent 的依赖有序任务清单"
---

# Tasks: 动态管理 Agent

**Input**: Design documents from `/specs/030-lesson30-dynamic-agent-management/`

**Prerequisites**: plan.md、spec.md、research.md、data-model.md、contracts/、quickstart.md

**Tests**: 本节课件明确要求 TDD harness；每个测试任务必须先落地并确认会在缺少实现时失败，再完成对应实现。不得删除断言、禁用测试或放宽门禁。

**Organization**: 任务按用户故事组织；所有路径均为仓库根目录相对路径。实现期间不自动 commit、push 或运行 package.sh。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 与同阶段其他任务操作不同文件且无未完成依赖，可并行
- **[Story]**: 映射到 spec.md 的用户故事

## Phase 1: Setup（共享目录）

**Purpose**: 建立课件明确要求的归档目录，不引入依赖或数据库变更。

- [X] T001 创建并保留删除归档目录 `.oryxos/archive/.gitkeep`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 先补齐第 29 节为动态生命周期预留的注销能力，并让路径与内存草稿共享同一 AgentLoader 校验。

- [X] T002 在 `oryxos-core/src/test/java/com/oryxos/core/scheduler/AgentSchedulerRegisterTest.java` 先增加 `unregisterProfile` 取消全部句柄、移除 binding、保留 taskLocks/历史状态的失败测试
- [X] T003 在 `oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java` 实现同步 `unregisterProfile(Profile)`，逐 schedule 取消 `ScheduledFuture`、移除运行绑定并协调既有任务状态
- [X] T004 在 `oryxos-core/src/test/java/com/oryxos/core/profile/AgentLoaderTest.java` 先增加路径加载与内存 AGENT.md 文本使用同一拆分、name/provider/tool 校验的测试
- [X] T005 在 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java` 提取同包可复用的内存文档派生入口，并让现有 `deriveProfile(Path)` 委托它，禁止生成校验写临时 Agent 目录

**Checkpoint**: 注销与统一校验基础就绪，后续 CRUD、Watcher、Generate 均可复用。

---

## Phase 3: User Story 1 - 通过接口动态管理 Agent（Priority: P1）🎯 MVP

**Goal**: 通过 REST 创建、列表、详情、更新和删除 Agent；创建立即可用，更新/删除顺序正确，失败不留下半个 Agent。

**Independent Test**: 创建带 schedule 的最小 Agent 后立即查询/调用；更新 schedule 后仅新句柄存在；删除后运行时不可见且完整目录归档；注入每一步失败验证回滚。

### Tests for User Story 1

- [X] T006 [P] [US1] 新建 `oryxos-core/src/test/java/com/oryxos/core/profile/AgentLifecycleServiceTest.java`，原样覆盖课件关键测试 `注册失败_必须回滚已写的Agent目录_不留半个Agent`、`删除必须先停定时_再动索引和目录`，并用 InOrder 覆盖 create 顺序、冲突零写入、update schedule 先 unregister 后 register、恢复原定义
- [X] T007 [P] [US1] 扩展 `oryxos-web/src/test/java/com/oryxos/web/controller/AgentApiControllerTest.java`，先为 create/list/get/update/delete 薄转发、统一 ApiResponse、冲突 400、缺失 404 与既有 invoke 不变增加失败测试

### Implementation for User Story 1

- [X] T008 [P] [US1] 在 `oryxos-core/src/main/java/com/oryxos/core/exception/StandardErrorCode.java` 增加契约规定的 40001 Agent 冲突、40002 Agent 定义非法、40003 生成草稿非法业务码并保持既有 HTTP 分类规则
- [X] T009 [P] [US1] 新建 `oryxos-core/src/main/java/com/oryxos/core/model/AgentView.java`，表达安全 Agent 定义视图并明确排除 Provider API key、通知密钥和绝对路径
- [X] T010 [P] [US1] 新建 `oryxos-web/src/main/java/com/oryxos/web/dto/CreateAgentRequest.java` 与 `oryxos-web/src/main/java/com/oryxos/web/dto/UpdateAgentRequest.java`，实现 raw/structured 互斥、必填与部分更新校验并生成不含明文凭证的 AGENT.md
- [X] T011 [US1] 新建 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentStore.java`，实现安全名称检查、根路径约束、AGENT.md 同目录临时文件原子写/读取/恢复、创建回滚删除、完整目录唯一归档和稳定目录树所需只读能力
- [X] T012 [US1] 新建 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLifecycleService.java`，实现按 Agent 名称串行的 create/register/list/get/update/delete；复用 AgentLoader/ProfileRegistry/AgentScheduler，确保创建失败清理文件与运行时、更新失败恢复原文件与原注册、删除严格按注销→移出索引→归档
- [X] T013 [US1] 扩展 `oryxos-web/src/main/java/com/oryxos/web/controller/AgentApiController.java`，增加 POST create、GET list/detail、PUT update、DELETE delete，映射 AgentView 并保留现有 POST `/{name}/invoke` 行为
- [X] T014 [US1] 在 `oryxos-core/src/main/java/com/oryxos/core/config/CoreAutoConfiguration.java` 装配 AgentStore 与 AgentLifecycleService 所需根路径和既有依赖，不新增反向模块依赖
- [X] T015 [US1] 运行 `./mvnw -pl oryxos-core,oryxos-web -am test -Dtest=AgentLifecycleServiceTest,AgentApiControllerTest,AgentSchedulerRegisterTest,AgentLoaderTest -Dsurefire.failIfNoSpecifiedTests=false` 并修复本故事失败

**Checkpoint**: REST CRUD 可独立演示，Agent 创建后无需重启即可使用，失败和删除均不遗留不一致状态。

---

## Phase 4: User Story 2 - 手工投放目录即时生效（Priority: P2）

**Goal**: 启动扫描、API 写入和手工目录新增/修改/移走都汇入同一个 register(Path)，5 秒内同步运行时且单个坏目录不终止监听。

**Independent Test**: 运行 Watcher 后在临时 agents 根目录增改删 Agent 子目录，观察 Profile 与 schedule 同步变化；写入非法目录后继续处理下一个合法目录。

### Tests for User Story 2

- [X] T016 [US2] 新建 `oryxos-core/src/test/java/com/oryxos/core/profile/WorkspaceWatcherTest.java`，先覆盖启动全量扫描、手工新增触发同一 register、AGENT.md 修改重注册、根目录删除注销、API 重复事件幂等、坏目录不中断与 close 释放线程/WatchService

### Implementation for User Story 2

- [X] T017 [US2] 新建 `oryxos-core/src/main/java/com/oryxos/core/profile/WorkspaceWatcher.java`，使用一个可关闭守护线程监听 agents 根目录及 Agent 子目录，启动同步全量扫描，事件调用 AgentLifecycleService.register/同包注销并逐事件记录异常
- [X] T018 [US2] 修改 `oryxos-core/src/main/java/com/oryxos/core/config/CoreAutoConfiguration.java`，用 WorkspaceWatcher 的同步初扫替换启动时直接 `AgentLoader.scanAndRegister`，保持旧 Profile YAML 兼容加载与 schedule 初始化顺序，关闭应用时可靠停止 Watcher
- [X] T019 [US2] 运行 `./mvnw -pl oryxos-core -am test -Dtest=WorkspaceWatcherTest,AgentScanRegisterTest,AgentLoaderTest,AgentSchedulerRegisterTest -Dsurefire.failIfNoSpecifiedTests=false` 并修复监听与第 29 节回归

**Checkpoint**: 不走 API 手工投放也可独立完成实时上线、更新和注销。

---

## Phase 5: User Story 3 - 一句话生成 Agent 草稿（Priority: P3）

**Goal**: 通过专用 MiniMax 配置把一句话生成可解析 AGENT.md 草稿，人在确认前不产生文件、Profile 或 schedule，调用成败均沿既有路径审计。

**Independent Test**: mock ProviderService 返回合法/非法文本，合法草稿通过 AgentLoader 内存校验且所有存储/注册 mock 零调用；非法结果返回 40003；Provider 调用带配置的 minimax/MiniMax-M2.7 与 SessionManager 生成的 sessionId。

### Tests for User Story 3

- [X] T020 [US3] 新建 `oryxos-core/src/test/java/com/oryxos/core/profile/GenerateTest.java`，先覆盖合法草稿可解析且不落盘/不注册、非法草稿 40003、Provider 异常透传审计路径、显式 MiniMax 配置与 SessionManager 会话身份

### Implementation for User Story 3

- [X] T021 [P] [US3] 新建 `oryxos-core/src/main/java/com/oryxos/core/config/AgentGenerationProperties.java`，绑定已确认的 `oryxos.agent-generation.provider/model` 并设置 `minimax`/`MiniMax-M2.7` 默认值
- [X] T022 [P] [US3] 在 `oryxos-boot/src/main/resources/application.yaml` 增加 `oryxos.agent-generation.provider: minimax` 与 `model: MiniMax-M2.7`，继续由现有 Provider 映射从环境变量取得密钥
- [X] T023 [P] [US3] 新建 `oryxos-web/src/main/java/com/oryxos/web/dto/GenerateAgentRequest.java`，校验 sentence 非空且不超过 4096 字符
- [X] T024 [US3] 在 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLifecycleService.java` 实现 generate：用 SessionManager 创建生成会话、按 AgentGenerationProperties 构造临时 Profile、调用 ProviderService 文本入口、以内存 AgentLoader 规则校验并原样返回合法草稿，不调用 Tool
- [X] T025 [US3] 在 `oryxos-web/src/main/java/com/oryxos/web/controller/AgentApiController.java` 增加 POST `/generate`，并补齐 `oryxos-web/src/test/java/com/oryxos/web/controller/AgentApiControllerTest.java` 的生成薄转发与非法草稿 400 契约后运行 GenerateTest/AgentApiControllerTest

**Checkpoint**: generate 可独立演示，只有正式 create 才改变 Agent 工作区和运行时。

---

## Phase 6: User Story 4 - 在管理平台管理和浏览工作区（Priority: P4）

**Goal**: 提供只读工作区 tree/file API，并在现有管理台完成生成预览、Agent CRUD、目录浏览与错误反馈。

**Independent Test**: MockMvc 验证 tree/file 正常与路径越界；浏览器完成生成→编辑→创建→详情→更新→工作区查看→二次确认删除，服务错误 message 可见。

### Tests for User Story 4

- [X] T026 [US4] 新建 `oryxos-web/src/test/java/com/oryxos/web/controller/WorkspaceApiControllerTest.java`，先覆盖 agents/archive 稳定树、正常 UTF-8 文件、缺失 404、目录/绝对路径/`../../etc/passwd` 400，以及工作区内软链接指向外部时 400

### Implementation for User Story 4

- [X] T027 [P] [US4] 新建 `oryxos-web/src/main/java/com/oryxos/web/dto/FileNode.java`，只返回名称、工作区相对路径、DIRECTORY/FILE、readable 与稳定排序 children
- [X] T028 [US4] 新建 `oryxos-web/src/main/java/com/oryxos/web/controller/WorkspaceApiController.java`，实现 GET `/api/v1/workspace/tree` 与 `/file`；调用 AgentStore 的规范路径/真实路径双重校验，只读 agents/archive 内普通 UTF-8 文本
- [X] T029 [P] [US4] 修改 `oryxos-web/src/main/resources/static/admin/index.html`，新增 Agent 管理与工作区导航/面板、生成输入、可编辑草稿、CRUD 表单、删除确认区域和文件预览语义结构
- [X] T030 [US4] 修改 `oryxos-web/src/main/resources/static/admin/app.js`，接入 Agent generate/CRUD 与 workspace tree/file，复用 ApiResponse 请求处理，渲染目录展开和只读文本，删除前二次确认并显示服务端 message
- [X] T031 [P] [US4] 修改 `oryxos-web/src/main/resources/static/admin/styles.css`，补充响应式表单、模态确认、目录树、编辑器与只读文件预览样式及键盘焦点状态
- [X] T032 [US4] 扩展 `oryxos-web/src/test/java/com/oryxos/web/WebSmokeIT.java`，为两个新页面入口与静态资源、Agent/workspace 路由增加 `@Tag("integration")` 冒烟断言
- [X] T033 [US4] 运行 `./mvnw -pl oryxos-web -am test -Dtest=WorkspaceApiControllerTest,AgentApiControllerTest -Dsurefire.failIfNoSpecifiedTests=false` 并修复工作区安全和前端依赖回归

**Checkpoint**: 运营可只通过管理平台完成课件要求的完整管理与浏览流程。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 收紧竞态、安全、文档与全量门禁，产出节级验收证据。

- [X] T034 [P] 在 `oryxos-core/src/test/java/com/oryxos/core/profile/AgentLifecycleServiceTest.java`、`WorkspaceWatcherTest.java` 与 `oryxos-web/src/test/java/com/oryxos/web/controller/WorkspaceApiControllerTest.java` 补齐 API/Watcher 并发重复事件、分步写入重试、归档重名、更新恢复失败日志和密钥不出响应的边界断言
- [X] T035 [P] 更新 `docs/TechnicalSolution.md` 与 `docs/class/第30节：Web Service 动态管理 Agent.md` 中仅因最终代码签名产生的必要对齐内容；不得改变已确认的端点、配置键或一个目录模型
- [X] T036 运行 `./mvnw clean verify`，确认 12 个模块、前序全部测试、Spotless、Checkstyle、P3C/PMD、SpotBugs/FindSecBugs 与 OWASP 门禁全绿
- [X] T037 按 `specs/030-lesson30-dynamic-agent-management/quickstart.md` 完成不触发真实业务通知的启动冒烟：MiniMax generate 不落盘、create 即可用、手工目录 5 秒内上线、workspace 越界 400、delete 归档，并记录暂缓的真实 cron/webhook/UI 人工项
- [X] T038 新建 `specs/030-lesson30-dynamic-agent-management/acceptance-report.md`，逐项记录 harness 类与关键测试、交付物存在性、前序回归、H4 六条不变量、完整 Maven 输出、真实冒烟证据和剩余人工确认清单

---


## Phase 8: Approved Agent Memory Compatibility Extension

**Purpose**: 按用户确认的兼容方案把长期记忆与 Agent 关联，并调整管理台信息架构。

- [X] T039 在 `LongTermMemoryTest`、`MemoryServiceTest`、`MemoryToolsTest` 先增加 Agent 专属/历史共享过滤、Prompt 注入、ProfileContext 工具路由测试，并新增 `MemoryApiControllerTest` 查询兼容测试
- [X] T040 扩展 `MemoryService`/`LongTermMemory`/`MemoryTools`，在单个 `MEMORY.md` 中为新条目写入 Agent 标记，按 Agent 过滤 Prompt 与检索，同时保留无上下文旧调用和历史无标记条目
- [X] T041 扩展 `GET /api/v1/memory` 的可选 `agent` 参数，不新增端点、DTO、配置或数据库表
- [X] T042 在 Agent 详情增加记忆 Tab 和关联查询，删除 OS运行时的会话、长期记忆子菜单与内容区，同时保留概览会话统计
- [X] T043 更新 WebSmokeIT、SDD 产物与验收报告，并运行聚焦测试和 `mvn clean verify`


## Phase 9: daily-reconcile Shell Invocation Regression Fix

**Purpose**: 修复 MiniMax 在定时触发时把整行命令或 `cmd` 字段传给 shell，导致空命令/可执行文件不存在的问题。

- [X] T044 从 `tool_invocations.input_json` 审计确认失败参数分别为组合 command、未声明 cmd 与白名单外 find
- [X] T045 将 daily-reconcile 正文和第29节课件改为精确结构化调用 `{"command":"python3","args":["scripts/reconcile.py"]}`，明确禁止组合 command、cmd 与路径探测；通知渠道对齐 minimax-agent，并在脚本返回 error 时停止
- [X] T046 扩展仓库真实示例解析测试，使用隔离 CSV 执行脚本，并运行 `mvn clean verify`

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无依赖。
- **Phase 2 Foundational**: 依赖 T001；阻塞全部用户故事。
- **US1 (Phase 3)**: 依赖 Phase 2，形成 CRUD MVP。
- **US2 (Phase 4)**: 依赖 US1 的 `AgentLifecycleService.register/unregister`。
- **US3 (Phase 5)**: 依赖 Phase 2 与 US1 的生命周期服务；不依赖 Watcher。
- **US4 (Phase 6)**: 工作区 API 依赖 AgentStore；管理页面依赖 US1/US3 API；可在 US2 完成前开发静态结构。
- **Polish (Phase 7)**: 依赖计划纳入的全部用户故事。

### User Story Dependencies

```text
Foundational
    └── US1 REST CRUD / 生命周期（MVP）
          ├── US2 WorkspaceWatcher
          ├── US3 一句话生成
          └── US4 工作区 API + 管理平台
                 └── Polish / 完整验收
```

### Within Each User Story

- 必须先完成并运行测试任务，确认缺少实现时失败，再写实现。
- 值对象/DTO 与底层存储先于编排服务，服务先于 Controller/UI。
- 更新与删除必须保留课件指定的调用顺序断言。
- 每个故事 checkpoint 通过后再进入下一阶段，失败当场修复。

### Parallel Opportunities

- T006 与 T007 可并行编写 core/web 失败测试。
- T008、T009、T010 操作不同文件，可并行。
- T021、T022、T023 操作配置类、YAML、Web DTO，可并行。
- T027、T029、T031 操作 DTO、HTML、CSS，可并行。
- T034 与 T035 可在实现稳定后并行，最终统一由 T036 判卷。

---

## Parallel Example: User Story 1

```text
Task T006: 编写 AgentLifecycleServiceTest 的创建/更新/删除顺序与回滚测试
Task T007: 编写 AgentApiControllerTest 的 CRUD 契约测试

Task T008: 扩展 StandardErrorCode
Task T009: 新建安全 AgentView
Task T010: 新建 CreateAgentRequest/UpdateAgentRequest
```

## Parallel Example: User Story 4

```text
Task T027: 新建 FileNode DTO
Task T029: 增加管理台 HTML 结构
Task T031: 增加管理台样式
```

---

## Implementation Strategy

### MVP First

1. 完成归档目录和 Foundational。
2. 完成 US1 的 AgentStore、生命周期 CRUD、Controller 与 harness。
3. 停在 US1 checkpoint，用 mock 与临时目录证明创建即用、失败回滚、更新/删除顺序。
4. 再按 US2 → US3 → US4 增量交付。

### Incremental Delivery

1. **US1**：API 动态管理闭环。
2. **US2**：手工目录免重启，与 API 汇入同一注册路径。
3. **US3**：MiniMax 一句话生成，人在环中确认。
4. **US4**：工作区浏览与管理页面。
5. **Polish**：竞态/安全边界、全量门禁、真实冒烟与验收报告。

## Notes

- 课件、技术方案与用户 clarify 决定优先于实现便利。
- 不新增第三方依赖或数据库表。
- 不实现认证、在线文件编辑、zip/multipart、Agent 启停、dry-run、版本历史或多轮生成。
- 不通过序列化 Profile 暴露已展开的 API key。
- 不自动 commit、push 或运行 package.sh。
