# Feature Specification: Lesson 25 - Scheduled Task Module (AgentScheduler)

**Feature Branch**: `025-lesson25-scheduler`  
**Created**: 2026-09-11  
**Status**: Draft  
**Input**: 第25节需求：定时任务模块（AgentScheduler）——第三种触发源（钟推）驱动 Agent 自主到点干活

---

## 1. Background & Value (背景与价值)

前面章节实现的 CLI 交互与 Web Service 接口调用，本质都是"人推"（由用户或外部系统主动发起消息）。但在很多企业业务场景中（例如早晨 9:00 自动巡检、每日技术早报、定时天气播报），Agent 必须能够脱离人的手动干预，到点自主发起对话。

定时任务不是一种新的执行逻辑，而是平级的第三种触发源（"钟推"）。它负责在预定时间点装配一条消息，无缝交由与 CLI/Web 完全一致的 `AgentService` 入口执行。ReActLoop 推理、Tool 执行、LLM Provider 调用及全链路审计完全复用，一行不改。

---

## 2. User Scenarios & Testing (用户场景与独立测试)

### User Story 1 - 基于 Profile 配置的动态定时任务注册与时区保障 (Priority: P1)
作为系统管理员，我希望在 Agent 的 `AGENT.md` frontmatter 中配置 `schedules` 列表（定义 cron 表达式、指定时区如 `Asia/Shanghai`、触发提示词），系统启动时自动读取所有 Profile 并动态注册进 Spring 调度器。触发时严格按声明时区计算时间点，不被宿主机默认时区干扰。

**Why this priority**: 配置驱动是 "配置即 Agent" 的基石，动态注册使得改配置无需重新编译代码。

**Independent Test**:
- 构造包含 `schedules` 的 Profile 注入 `ProfileRegistry`；
- 调用 `AgentScheduler.registerAll()`，通过 `ArgumentCaptor` 捕获注册进 `TaskScheduler` 的任务与 `CronTrigger`；
- 断言 cron 表达式与 `ZoneId` 100% 吻合。

**Acceptance Scenarios**:
1. **Given** Profile 配置了 `cron: "0 0 9 * * ?"` 与 `timezone: "Asia/Shanghai"`，**When** 系统启动调度器注册，**Then** `CronTrigger` 以 `Asia/Shanghai` 时区精确注册。
2. **Given** Profile 未配置时区（为 null 或空字符串），**When** 系统注册任务，**Then** 安全回退至系统默认时区 `ZoneId.systemDefault()`。

---

### User Story 2 - 本地内存锁防任务重叠执行 (Priority: P1)
作为系统管理员，当某次定时任务的 ReAct 循环或工具执行耗时较长（超过了两次调度的时间间隔）时，我希望下一次调度触发安全跳过本次执行，绝不并发重叠跑两份，防止资源耗尽与数据状态竞争。

**Why this priority**: 防止因大模型推理延迟或网络波动导致的任务雪崩与并发脏写。

**Independent Test**:
- 模拟某个任务 ID 当前已持有锁（正在执行中）；
- 触发该任务的 `runOnce` 执行；
- 断言 `AgentService.process` 一次都没有被调用（直接跳过），并记录提示日志。

**Acceptance Scenarios**:
1. **Given** 任务 `task-1` 正在运行中（锁被占用），**When** 定时器再次触发该任务，**Then** 本次触发被跳过，`AgentService.process` 未被再次调用。

---

### User Story 3 - 失败隔离与死锁防御（Finally 保证释放锁） (Priority: P1)
作为系统管理员，当某次定时任务执行抛出任何未预期异常（如大模型超额限流、网络中断）时，我希望调度器能够安全捕获并记录结构化日志，不波及 Spring 调度线程池；更重要的是，任务锁必须在 `finally` 块中绝对释放，确保后续周期到达时任务能够正常恢复执行，不发生死锁。

**Why this priority**: 健壮的后台任务必须做到"单次失败不崩溃、异常过后不卡死"。

**Independent Test**:
- Mock `AgentService.process` 抛出运行时异常；
- 调用 `runOnce`，断言方法不抛出异常；
- 紧接着再次调用 `runOnce`，断言 `AgentService.process` 能够再次被调用，证明锁已经被安全释放。

**Acceptance Scenarios**:
1. **Given** `AgentService.process` 执行失败抛出异常，**When** 调度器执行该次任务，**Then** 异常被捕获记录日志且不外抛。
2. **Given** 任务经历了一次失败异常，**When** 下一周期到达再次触发，**Then** 能够正常获取锁并执行。

---

### User Story 4 - 会话身份三元组统一与历史累积 (Priority: P2)
作为 ReAct 循环引擎，定时触发时需要拥有明确的 Session。系统统一使用固定三元组（`channel = "scheduler"`, `user = "scheduler"`, `profileName = profile.getName()`）通过 `SessionManager.getOrCreate` 获取会话，使历次定时任务的对话历史自然累积，同时享受 `max_history_turns` 自动截断保护。

**Why this priority**: 保持触发源身份一致性，不为定时任务发明新概念，完全复用既有 Session 机制。

**Independent Test**:
- 调用 `runOnce`，验证传入 `AgentService.process` 的 Session，其 `channel` 和 `userId` 均为 `"scheduler"`，`profileName` 与当前 Profile 一致。

---

## 3. Edge Cases (边界条件与防御)

1. **Profile 无任何 schedules**：未配置定时任务的 Profile 在 `registerAll` 时安全跳过，不抛异常。
2. **时区字符串非法**：配置了不存在或非法的时区标识（如 `"Invalid/Zone"`）时，捕获 `ZoneRulesException` / `DateTimeException` 并友好回退到默认时区或记录告警，不使注册过程崩溃。
3. **空 cron 或空 message**：未配置合法 cron 或 message 为空时，拒绝注册或记录警告跳过该条任务。
4. **任务 ID 缺省**：若 YAML 配置未显式指定 `id`，自动以 `profile.getName() + "-" + index` 或 `profile.getName() + "-" + cron` 派生唯一稳定的任务 ID，确保锁隔离。

---

## 4. Requirements (功能需求清单)

### Functional Requirements
- **FR-001**: 系统 MUST 在 `ScheduleConfig` 中提供 `id`、`cron`、`timezone`、`message` 完整属性，并支持获取 `ZoneId`。
- **FR-002**: 系统 MUST 在 `AgentScheduler` 启动时读取 `ProfileRegistry` 中所有 Profile 的 `schedules` 列表并动态注册进 `ThreadPoolTaskScheduler`。
- **FR-003**: 系统 MUST 使用 `CronTrigger(cron, zoneId)` 进行定时规则绑定，杜绝使用硬编码 `@Scheduled` 注解。
- **FR-004**: 系统 MUST 为每个定时任务 ID 维护独立的 `ReentrantLock`，通过 `tryLock()` 实现防重叠执行与直接跳过。
- **FR-005**: 系统 MUST 在 `runOnce` 中统一使用固定三元组 `("scheduler", "scheduler", profile.getName())` 获取或创建会话。
- **FR-006**: 系统 MUST 调用 `AgentService.process(session, message)` 将定时触发无缝汇入 ReAct 统一处理中枢。
- **FR-007**: 系统 MUST 在 `finally` 块中无条件释放任务锁（`lock.unlock()`），杜绝异常导致的永久死锁。
- **FR-008**: 系统 MUST 捕获 `runOnce` 内部的所有执行异常，记录错误日志而不向调度器主线程传播。
- **FR-009**: 系统 MUST 在 `CoreAutoConfiguration` 中提供 `TaskScheduler` 与 `AgentScheduler` 的条件自动装配。

---

## 5. Non-Goals & Boundaries (明确不做)

1. **不做分布式锁与租约选主**：核心阶段为单体应用，仅使用进程内内存锁。
2. **不做失败重试与告警通知工作流**：失败由日志与审计表兜底，不引入工作流引擎复杂度。
3. **不做通过 REST API 动态增删任务**：核心阶段配置来源于 Profile 文件，动态端点属于扩展阶段。

---

## 6. Acceptance Criteria (验收标准与 Harness)

### 自动化 Harness 测试套件（`AgentSchedulerTest`）
1. `注册时CronTrigger携带配置cron和时区`：捕获 `taskScheduler.schedule` 参数，断言 cron 与 ZoneId 准确对应。
2. `上一次还没跑完_本次触发直接跳过`：加锁状态下调用 `runOnce`，断言 `agentService.process` 未被执行。
3. `任务抛异常_不外抛且锁必须被释放`：`agentService.process` 抛出异常后不外抛，二次触发正常执行。
4. `会话三元组固定_复用同一Session`：断言 Session 的 channel、user 均为 `"scheduler"`。

### 人工验收项
- 真实定时触发一次：配置每分钟触发，在控制台观察到自动发起对话并记录审计。
- 改 cron 免编译：修改 YAML 重新启动即按新周期运行。
