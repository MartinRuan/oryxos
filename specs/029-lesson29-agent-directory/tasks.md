---
description: "第29节：目录型 Agent 的依赖有序实施任务"
---

# Tasks: 一个目录定义一个会自己运行的 Agent

**Input**: Design documents from `/specs/029-lesson29-agent-directory/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/agent-directory-contract.md`

**Tests**: 本节课件明确要求六个 harness 类。每个故事先写测试并确认红灯，再实现对应代码。

**Organization**: 按 P1 目录安装、P2 渐进式披露、P3 运行时注册三个用户故事分组；第 29 节要求顺序实施。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 文件互不冲突且不依赖未完成任务时可并行
- **[Story]**: 对应 spec.md 的 US1/US2/US3
- 所有任务均包含具体文件路径

## Phase 1: Setup

**Purpose**: 固定修改前基线和本节实施范围。

- [X] T001 运行 `./mvnw -pl oryxos-core,oryxos-tool -am test` 建立第 16～28 节回归基线，并仅在失败时定位到对应模块的 `pom.xml` 或既有测试后停下报告

**Checkpoint**: 既有核心与工具模块测试全绿后进入 TDD。

---

## Phase 2: Foundational Decisions

**Purpose**: 本节不新增共享基础设施；实现必须遵循现有 Profile、Scheduler、ContextLoader 和 Sandbox 契约。

- [X] T002 核对并在实现中保持 `specs/029-lesson29-agent-directory/contracts/agent-directory-contract.md` 的目录名等于 Agent 名、私有 Skill、先校验后注册、无新 Profile 字段四项边界

**Checkpoint**: 不需要数据库迁移、依赖升级、REST 路径或新配置键。

---

## Phase 3: User Story 1 - 投放目录即可安装 Agent (Priority: P1) 🎯 MVP

**Goal**: 扫描自足 Agent 目录，解析 AGENT.md、派生 Profile、注册并接入启动调度。

**Independent Test**: 在临时根目录放入 N 个 Agent 目录，扫描后 registry 恰好新增 N 个；缺 name/provider 明确失败；全部 frontmatter 字段和 schedules 保持原值；未知工具产生带 Agent/工具名的 WARN。

### Tests for User Story 1

- [X] T003 [P] [US1] 先编写并确认失败的目录解析与缺字段 harness `oryxos-core/src/test/java/com/oryxos/core/profile/AgentLoaderTest.java`，覆盖 frontmatter/正文分离、skills/scripts/REFERENCE 资源识别、缺 name/provider 点名
- [X] T004 [P] [US1] 先编写并确认失败的字段映射 harness `oryxos-core/src/test/java/com/oryxos/core/profile/DeriveProfileTest.java`，覆盖 identity/provider/tools/notify_channels/bootstrap/settings 及 schedules 原样派生
- [X] T005 [P] [US1] 先编写并确认失败的批量扫描 harness `oryxos-core/src/test/java/com/oryxos/core/profile/AgentScanRegisterTest.java`，覆盖 N 个直接子目录、坏目录无部分状态、未知工具 WARN、合法 Agent 注册及 schedule 交给调度器

### Implementation for User Story 1

- [X] T006 [US1] 新建 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java`，实现 AGENT.md 拆分、内部资源路径识别、`deriveProfile(Path)`、目录名一致性校验及稳定顺序的 `scanAndRegister(Path)`
- [X] T007 [US1] 修改 `oryxos-core/src/main/java/com/oryxos/core/config/CoreAutoConfiguration.java`，让启动装配使用 AgentLoader 扫描 `.oryxos/agents/`，保留旧 Profile 来源兼容，并保证 Profile 加载先于调度注册
- [X] T008 [P] [US1] 按第29节课件原文创建示例 `.oryxos/agents/daily-reconcile/AGENT.md`、`.oryxos/agents/daily-reconcile/REFERENCE.md`、`.oryxos/agents/daily-reconcile/skills/report-format.md`、`.oryxos/agents/daily-reconcile/scripts/reconcile.py`，所有凭证使用环境变量
- [X] T009 [US1] 运行 `AgentLoaderTest`、`DeriveProfileTest`、`AgentScanRegisterTest` 及既有 `ProfileLoaderTest`，修复 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java` 与启动装配直到全绿

**Checkpoint**: 新增 Agent 只需投放目录即可被发现并派生为既有 Profile，示例四个文件完整。

---

## Phase 4: User Story 2 - 资源按需进入上下文 (Priority: P2)

**Goal**: 每轮只注入 AGENT.md 正文，私有子指令、参考和脚本按需访问，并补足解释器脚本目录边界。

**Independent Test**: 构造带独特标记的正文、frontmatter、Skill、参考和脚本，ContextLoader 结果只含正文；修改正文后重读生效；解释器只接受 Agent scripts 目录内的脚本路径。

### Tests for User Story 2

- [X] T010 [P] [US2] 先编写并确认失败的渐进式披露 harness `oryxos-core/src/test/java/com/oryxos/core/context/ProgressiveDisclosureTest.java`，覆盖正文注入、frontmatter/Skill/参考/脚本不预载及正文修改即时生效
- [X] T011 [P] [US2] 先扩充并确认失败的脚本边界测试 `oryxos-tool/src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java`，覆盖 Agent scripts 内路径允许、目录外脚本和解释器命令字符串拒绝

### Implementation for User Story 2

- [X] T012 [US2] 修改 `oryxos-core/src/main/java/com/oryxos/core/context/impl/ContextLoaderImpl.java`，按 profile.name 每轮重读目录型 AGENT.md 并剥离 frontmatter，目录型 Agent 不预载私有资源，同时保留非目录型旧 Profile 行为
- [X] T013 [US2] 修改 `oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTools.java`，在既有命令白名单和 argv 直传基础上，将解释器脚本参数规范化并限制到 `.oryxos/agents/<name>/scripts/`，拒绝 `-c` 等绕过形式
- [X] T014 [US2] 修改 `oryxos-boot/src/main/resources/application.yaml` 的既有 `shell.allowed-commands` 值，加入示例所需解释器但不新增配置键
- [X] T015 [US2] 运行 `ProgressiveDisclosureTest`、既有 `ContextLoaderTest`、`ShellToolsTest` 与 `WhitelistSandboxTest`，修复上下文和沙箱实现直到新旧语义全绿

**Checkpoint**: Prompt 只常驻主正文，私有资源内容命中数为零，脚本解释器同时受命令和目录限制。

---

## Phase 5: User Story 3 - 启动与运行时注册保持一致 (Priority: P3)

**Goal**: 注册中心支持并发增删查，批量/单 Agent 调度复用相同逻辑并保存句柄，为第 30 节生命周期 API 铺路。

**Independent Test**: register 后立即可见、remove 后 exists 为 false；同一非法配置经文件加载和运行时注册产生同类同消息；registerProfile 为每个启用 schedule 保存匹配 cron/时区的句柄。

### Tests for User Story 3

- [X] T016 [P] [US3] 先编写并确认失败的运行时注册 harness `oryxos-core/src/test/java/com/oryxos/core/profile/ProfileRegistryRuntimeTest.java`，覆盖 register/remove/exists、并发可见性及启动/运行时非法配置异常类型和消息一致
- [X] T017 [P] [US3] 先编写并确认失败的单 Agent 调度 harness `oryxos-core/src/test/java/com/oryxos/core/scheduler/AgentSchedulerRegisterTest.java`，覆盖 `registerProfile`、ScheduledFuture 句柄存在、cron/时区透传和重复注册不产生双句柄

### Implementation for User Story 3

- [X] T018 [US3] 修改 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileRegistry.java` 与 `oryxos-core/src/main/java/com/oryxos/core/profile/ProfileLoader.java`，统一必填/Provider 校验消息，接入已有 Provider/Tool 集合，新增 `remove(String)`、`exists(String)` 并保留 containsProfile 兼容
- [X] T019 [US3] 修改 `oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java`，提取 `registerProfile(Profile)` 作为唯一单 Agent 循环体，复用既有 scheduledTasks 句柄 Map，正确替换同 Agent 旧句柄并让 `registerAll` 委托它
- [X] T020 [US3] 修改 `oryxos-core/src/main/java/com/oryxos/core/profile/AgentLoader.java` 与 `oryxos-core/src/main/java/com/oryxos/core/config/CoreAutoConfiguration.java`，让启动扫描和运行时单目录加载共同调用 registry.register 与 scheduler.registerProfile
- [X] T021 [US3] 运行 `ProfileRegistryRuntimeTest`、`AgentSchedulerRegisterTest`、`AgentScanRegisterTest`、既有 `ProfileLoaderTest`、`AgentSchedulerTest`、`AgentSchedulerLifecycleTest`，修复直到全绿

**Checkpoint**: 启动和运行时路径共享注册校验与单 Agent 调度逻辑，句柄可供下一节注销/更新使用。

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: 按课件与项目门禁做完整收敛。

- [X] T022 运行第29节六个 harness：`AgentLoaderTest`、`DeriveProfileTest`、`AgentScanRegisterTest`、`ProfileRegistryRuntimeTest`、`AgentSchedulerRegisterTest`、`ProgressiveDisclosureTest`，确认类存在、非空、无 `@Disabled` 且全部通过
- [X] T023 运行 `./mvnw clean verify`，修复格式、Checkstyle、P3C、PMD、SpotBugs/FindSecBugs 及全部前序回归，禁止删除断言或放宽测试
- [X] T024 按 `specs/029-lesson29-agent-directory/quickstart.md` 核对 daily-reconcile 四文件、启动扫描结果、定时列表和渐进式披露静态证据
- [X] T025 在 `specs/029-lesson29-agent-directory/acceptance-report.md` 记录 Maven 关键输出、六个 harness 映射、交付物存在性、前序回归、H4 六条全局不变量与剩余真模型/真数据库/真 webhook 人工项

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: T001 必须先通过。
- **Foundational Decisions**: T002 固定实现边界。
- **US1**: T003–T005 测试先行；T006/T007 实现后，T008 可独立创建示例，T009 收敛。
- **US2**: 依赖 US1 的 Agent 目录定位；T010/T011 测试先行，T012–T014 实现，T015 收敛。
- **US3**: 依赖 US1 的扫描链；T016/T017 测试先行，T018/T019 后由 T020 串联，T021 收敛。
- **Polish**: 依赖三个故事全部完成。

### User Story Dependencies

```text
Setup / Decisions
       |
       v
US1 目录扫描与派生
       |
       +--------> US2 上下文与脚本边界
       |
       +--------> US3 运行时注册与句柄
                         |
US2 + US3 --------------+
       |
       v
完整门禁与验收报告
```

### Parallel Opportunities

- T003、T004、T005 可在不同测试类并行编写。
- T008 与 T006/T007 文件互不冲突，可在 AgentLoader 接口明确后并行。
- T010 与 T011 分属 core/tool，可并行编写。
- T016 与 T017 分属 profile/scheduler，可并行编写。
- T018 与 T019 修改不同类，但 T020 必须等二者完成。
- 本仓库实施纪律仍按课节顺序收敛，每个故事完成测试后再进入下一故事。

## Parallel Examples

### US1

```text
Task T003: AgentLoaderTest
Task T004: DeriveProfileTest
Task T005: AgentScanRegisterTest
Task T008: daily-reconcile 四文件
```

### US2

```text
Task T010: ProgressiveDisclosureTest
Task T011: ShellToolsTest 脚本边界
```

### US3

```text
Task T016: ProfileRegistryRuntimeTest
Task T017: AgentSchedulerRegisterTest
```

## Implementation Strategy

### MVP First

先完成 T001–T009。此时“投放目录即可安装 Agent”可单独演示，尚不宣称第 29 节整体完成。

### Incremental Delivery

1. US1 建立目录 → Profile → 注册的主链。
2. US2 加入正文常驻、私有资源按需与脚本路径边界。
3. US3 统一启动/运行时注册并保存调度句柄。
4. T022–T025 运行全量门禁并生成验收证据。

## Notes

- 所有生产代码保持同步阻塞，不引入 Reactor、CompletableFuture 或自建线程池。
- 不新增 public 类型、Profile 字段、REST 路径、数据表或第三方依赖。
- 不把 Agent 目录、私有 Skill 或脚本注册为 Tool。
- 本任务清单不包含 commit、push 或 package.sh；由用户决定同步时机。
