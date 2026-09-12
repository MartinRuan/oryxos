# Research: Lesson 25 - Scheduled Task Module (AgentScheduler)

## 1. 核心技术调研与决策 (Key Technical Decisions)

### 1.1 触发源定位：钟推 vs 人推
- **平级架构定位**：定时任务不是 Agent 的新能力，而是与 CLI（交互输入）、Web Service（HTTP 调用）平级的第三种触发源。
- **统一入口汇流**：三大触发源最终全部调用 `AgentService.process(session, message)` 统一接口。ReAct 推理循环、工具调用、模型协议转换与全链路审计（`llm_calls`, `tool_invocations`）完全一致，无需额外定制。

### 1.2 动态调度机制：Spring TaskScheduler 与 CronTrigger
- **避免静态注解**：静态 `@Scheduled` 注解将 cron 表达式固定于编译期，违反"配置即 Agent"原则。
- **动态注册**：在系统启动后，读取所有 Profile 的 `schedules` 声明，通过 `TaskScheduler.schedule(Runnable, Trigger)` 动态注册。
- **显式时区支持**：使用 `new CronTrigger(cron, zoneId)`，保证调度严格基于配置指定的时区运行，杜绝服务器系统默认时区的不可预期影响。

### 1.3 防重叠执行：基于任务 ID 的内存局部锁
- **并发风险**：如果单次 ReAct 循环或工具执行时间超过两次调度周期，可能导致同一任务并发堆叠。
- **解决方案**：在 `AgentScheduler` 维护 `ConcurrentMap<String, Lock> taskLocks`。每次执行 `runOnce` 时执行 `lock.tryLock()`：
  - 获取锁成功：执行任务；
  - 获取锁失败：说明上一次调度未完成，直接跳过本次触发，记录提示日志，杜绝堆积。
- **单体架构约束**：核心阶段为单体部署，仅需进程内 `ReentrantLock`，无需引入分布式锁复杂度。

### 1.4 失败隔离与死锁防御
- **异常捕获**：`runOnce` 内部严格包裹 `try-catch-finally`，捕获所有 `Exception` 并记录 error 日志，防止未受控异常导致 Spring 调度线程崩溃。
- **锁释放保障**：在 `finally` 块中无条件执行 `lock.unlock()`，防止因任务执行失败导致任务被永久锁死。

### 1.5 会话身份设计
- **会话持久化**：沿用既有三元组签名 `("scheduler", "scheduler", profileName)`，同一个 Profile 的历次定时运行复用同一会话。
- **历史管理**：对话历史随运行自然累积，依靠 Profile 的 `max_history_turns` 自动截断保护上下文窗口。
