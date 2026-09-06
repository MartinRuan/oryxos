# Implementation Plan: 第22节 Memory 实现与代码讲解

**Branch**: `022-lesson22-memory` | **Date**: 2026-09-05 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/022-lesson22-memory/spec.md)

**Input**: Feature specification from `specs/022-lesson22-memory/spec.md`

## Summary

构建 OryxOS 统一记忆系统：实现 `MemoryService` 门面接口与标准实现 `MemoryServiceImpl`，以单文件两分区 Markdown（`.oryxos/memory/MEMORY.md`）为底座实现 `LongTermMemory` 长期记忆存储，坚持物理磁盘零缓存读写；归档区实施 4000 字符超长截断隔离防御，确保核心记忆一字不少；交付 `MemoryTools` 暴露 `save_memory` 与 `recall_memory` 内置工具供 Agent 自主读写与关键词检索；打通与 `PromptBuilder` 的集成注入点，使 Agent 具备跨会话偏好持久化能力。

## Technical Context

- **Language/Version**: Java 21 (LTS) with Virtual Threads enabled
- **Fixed Tech Stack**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **Module Allocation**: `全部→oryxos-memory`（按照架构宪章原则，`MemoryService` 与 `MemoryScope` 契约接口/值对象位于 `oryxos-core` 下包 `com.oryxos.memory`，实现类 `MemoryServiceImpl`、`LongTermMemory`、`MemoryTools` 及全部测试位于 `oryxos-memory`）
- **Testing Strategy**: `测试策略按课件"验收 harness"执行：LongTermMemoryTest、MemoryToolsTest、MemoryServiceTest（覆盖 写后立读无缓存、截断只裁归档核心区一字不动、scope 路由到正确区块、recallByKeyword 只搜归档区不区分大小写、scope 缺省写归档、关键词未命中返回友好提示不报错、buildContext 核心记忆与会话历史组合且归档区不整体注入），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **Syntax Forbidden**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

- [x] **原则一：自实现 ReAct Loop**（ReAct 循环自主调用 `PromptBuilder` 注入记忆，不引入第三方 Agent 抽象）
- [x] **原则二：Spring AI 职责限定**（`@Tool` 仅用于工具 Schema 生成与统一元数据暴露，禁用任何自动工具调用，执行完全由 `ToolExecutor` 接管）
- [x] **原则三：Provider 必须显式映射**（Memory 模块不影响 Provider 路由映射）
- [x] **原则四：一个目录 = 一个 Agent & 渐进式披露**（`MEMORY.md` 跨会话存储于 `.oryxos/memory/MEMORY.md`；`USER.md` 只读不写，`MEMORY.md` 读写）
- [x] **原则五：审计表 Day One 写入**（`save_memory` 和 `recall_memory` 工具调用成败均统一记录进 `tool_invocations`）
- [x] **原则六：应用层白名单沙箱**（Memory 工具直接操作本地受管记忆区，不穿透沙箱规则）
- [x] **原则七：同步阻塞执行模型**（基于 Java 21 虚拟线程同步阻塞模型，禁止引入 Reactor/CompletableFuture 等异步编程）
- [x] **原则八：Tool 模块三合一**（`MemoryTools` 注册为系统内置工具，供 `ToolRegistry` 统一纳管）

## Project Structure & Module Allocation

### Documentation (this feature)

```text
specs/022-lesson22-memory/
├── plan.md              # 本实施计划文档
├── research.md          # 架构调研与设计决策（Phase 0）
├── data-model.md        # 记忆数据模型与文件结构（Phase 1）
├── quickstart.md        # 验收验证与执行指南（Phase 1）
├── contracts/           # 契约定义
│   └── memory-contracts.md
└── tasks.md             # 任务依赖拆解清单（Phase 2）
```

### Source Code Allocation

- **oryxos-core**:
  - `com.oryxos.memory.MemoryService`（统一契约接口：`buildContext`, `remember`, `recall`）
  - `com.oryxos.memory.MemoryScope`（枚举：`CORE`, `ARCHIVAL`）
  - `com.oryxos.core.prompt.impl.PromptBuilderImpl`（集成点：组装时注入 `memoryService.buildContext(session)`）
- **oryxos-memory**:
  - `com.oryxos.memory.impl.MemoryServiceImpl`（`MemoryService` 门面标准实现）
  - `com.oryxos.memory.LongTermMemory`（物理文件读写、两分区管理、无缓存保障、截断保核心、关键词检索）
  - `com.oryxos.memory.MemoryTools`（Spring AI `@Tool` 注解与 `OryxTool` 工具适配：`save_memory`, `recall_memory`）
  - `com.oryxos.memory.config.MemoryAutoConfiguration`（Spring Boot 自动装配类）
  - `.oryxos/memory/MEMORY.md`（运行时记忆文件初始规范）
- **test**:
  - `com.oryxos.memory.LongTermMemoryTest`（覆盖写后立读、截断保核心、scope 路由、不区分大小写检索）
  - `com.oryxos.memory.MemoryToolsTest`（覆盖 scope 缺省路由、关键词未命中友好提示）
  - `com.oryxos.memory.MemoryServiceTest`（覆盖 buildContext 仅注入核心记忆、归档区不整体注入）
