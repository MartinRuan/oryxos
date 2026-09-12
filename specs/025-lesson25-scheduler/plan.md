# Implementation Plan: Lesson 25 - Scheduled Task Module (AgentScheduler)

**Branch**: `025-lesson25-scheduler` | **Date**: 2026-09-11 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/025-lesson25-scheduler/spec.md)

**Input**: Feature specification from `specs/025-lesson25-scheduler/spec.md`

## Summary

实现定时任务模块 `AgentScheduler`，作为平级的第三种触发源（"钟推"），以配置驱动的方式动态装载 Profile 的 `schedules` 规则。通过 Spring `TaskScheduler` + `CronTrigger` 绑定时区，利用基于任务 ID 的内存 `ReentrantLock` 防重叠执行，在异常时安全隔离并在 `finally` 中保证释放锁，统一委托给 `SessionManager` 与 `AgentService.process` 进行 ReAct 循环处理。

## Technical Context

- **固定技术栈**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **模块落位**: `AgentScheduler`/`ScheduleConfig`→`oryxos-core`。
- **测试策略**: `测试策略按课件"验收 harness"执行：AgentSchedulerTest（覆盖注册传参核对、重叠执行跳过、异常隔离与锁释放二进宫断言、会话三元组身份固定），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **语法禁区**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

1. **自实现 ReAct Loop**：`AgentScheduler` 仅作为触发源把消息喂给 `AgentService.process`，完全不触碰、不改动内部自实现的 ReAct 循环。
2. **Spring AI 职责限定**：仅做协议转换和 Schema 生成，禁止 Spring AI 自动执行工具，调度器完全通过 `AgentService` 进行统一调用。
3. **Provider 显式映射**：无直接依赖，由底层的 ProviderService 维持显式映射。
4. **Day One 审计表写入**：定时触发依然走标准的 `AgentService.process` 内部链路，所有的 `llm_calls` 与 `tool_invocations` 自动落库，不设任何审计旁路。
5. **代码规范与门禁**：Checkstyle（每行 <= 100 字符）、Spotless（Google Java Format）、Alibaba P3C（消除魔法值）、SpotBugs 零缺陷。

## Project Structure

### Documentation
```text
specs/025-lesson25-scheduler/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── scheduler-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code
```text
oryxos-core/src/main/java/com/oryxos/core/
├── model/
│   └── Profile.java                     # 完善 ScheduleConfig（id/cron/timezone/message/getZoneId）
├── profile/
│   └── ProfileLoader.java               # 解析 YAML schedules 中的 id 字段
├── scheduler/
│   └── AgentScheduler.java              # 核心调度器：registerAll, runOnce, taskLocks
└── config/
    └── CoreAutoConfiguration.java       # 配置 TaskScheduler 与 AgentScheduler Bean

oryxos-core/src/test/java/com/oryxos/core/scheduler/
└── AgentSchedulerTest.java              # 验收 Harness：时区参数核对、重叠跳过、异常隔离、锁释放验证
```
