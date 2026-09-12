# Requirements Checklist: Lesson 25 AgentScheduler

- [ ] `Profile.ScheduleConfig` 包含 id, cron, timezone, message 字段及 getZoneId 方法
- [ ] `ProfileLoader` 支持解析 schedule 中的 id, cron, timezone/zone, message
- [ ] `AgentScheduler` 位于 `oryxos-core`，基于 `TaskScheduler` 动态注册任务
- [ ] `AgentScheduler` 注册使用 `CronTrigger(cron, zoneId)`，严格遵循配置时区
- [ ] `AgentScheduler` 维护基于任务 ID 的内存 `ReentrantLock`
- [ ] `AgentScheduler` 在锁被占用时直接跳过本次执行并记录提示日志
- [ ] `AgentScheduler` 在 `runOnce` 中统一通过 `SessionManager.getOrCreate("scheduler", "scheduler", profile.getName())` 获取会话
- [ ] `AgentScheduler` 统一调用 `AgentService.process(session, message)`
- [ ] `AgentScheduler` 捕获所有异常记录日志而不外抛导致调度器崩溃
- [ ] `AgentScheduler` 在 `finally` 块中绝对释放锁，杜绝永久死锁
- [ ] `CoreAutoConfiguration` 中声明 `ThreadPoolTaskScheduler` 与 `AgentScheduler` 的 `@Bean`
- [ ] 验收 Harness `AgentSchedulerTest` 覆盖四个核心测试点全绿
