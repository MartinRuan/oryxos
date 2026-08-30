# Implementation Plan: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

**Branch**: `017-lesson17-react-loop` | **Date**: 2026-08-30 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/017-lesson17-react-loop/spec.md)

**Input**: Feature specification from `/specs/017-lesson17-react-loop/spec.md`

## Summary

实现 OryxOS 的 ReAct 推理与行动循环引擎（`ReActLoop`）、结构化 Prompt 组装器（`PromptBuilder`）、工具执行与沙箱前置审计器（`ToolExecutor`）、统一处理门面（`AgentService`）、ThreadLocal 隔离上下文（`ProfileContext`）、动态上下文加载器（`ContextLoader`）以及 `tool_invocations` 持久化实体与 Repository。

## Technical Context

- **Language/Version**: Java 21 (LTS) with Virtual Threads enabled
- **Primary Dependencies**: Spring Boot 3.x, Spring AI Alibaba (仅用于协议转换和 Schema，禁用自动执行), SnakeYAML, SLF4J
- **Storage**: SQLite + Spring Data JPA (手写 `schema.sql`，不依赖 `hibernate.ddl-auto=update`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test
- **Target Platform**: Linux / macOS / Windows server & developer environment
- **Constraints**: 
  - 严格自研 ReAct 循环（数十行 Java，不使用 Spring AI Agent 抽象）
  - Spring AI 严格限定协议转换与 Schema 生成，显式关闭自动工具执行
  - 审计表 Day One 写入：`tool_invocations` 表每次成功与失败均落库
  - 工具调用首行预留 `Sandbox.enforce` 沙箱检查位
  - `ProfileContext` 必须在 `finally` 严格清理
  - Prompt 组装每次重新读文件无缓存，末尾注入当前日期时间
  - 避免 P3C/ASM 无法解析的 Java 18+ 语法形态

## Constitution Check

- [x] **原则一：自实现 ReAct Loop**（不使用 Spring AI ChatClient 自动工具执行抽象）
- [x] **原则二：Spring AI 职责限定**（autoExecuteTools=false，ToolExecutor 集中执行）
- [x] **原则三：Provider 显式映射**（已在第16节完成，本节直接复用 ProviderService）
- [x] **原则四：一个目录 = 一个 Agent & 渐进式披露**（ContextLoader 支持无缓存读取）
- [x] **原则五：审计表 Day One 写入**（`tool_invocations` 成败均写入 SQLite）
- [x] **原则六：应用层白名单沙箱**（ToolExecutor 预留检查位）
- [x] **原则七：同步阻塞执行模型**（纯同步 + Virtual Threads，无响应式）

## Project Structure

### Documentation (this feature)

```text
specs/017-lesson17-react-loop/
├── plan.md              # 本计划文档
├── research.md          # 架构调研与设计决策
├── data-model.md        # 领域模型与 SQLite 表结构
├── quickstart.md        # 快速验证指南
├── contracts/           # 接口契约
│   └── react-and-context-contracts.md
└── tasks.md             # 任务拆解文档 (下一阶段生成)
```

### Source Code Allocation

- `oryxos-core`:
  - `com.oryxos.core.model.Session`
  - `com.oryxos.core.model.ToolResult`
  - `com.oryxos.core.session.SessionManager`
  - `com.oryxos.core.react.ReActLoop`, `com.oryxos.core.react.impl.ReActLoopImpl`
  - `com.oryxos.core.prompt.PromptBuilder`, `com.oryxos.core.prompt.impl.PromptBuilderImpl`
  - `com.oryxos.core.tool.ToolExecutor`, `com.oryxos.core.tool.impl.ToolExecutorImpl`
  - `com.oryxos.core.service.AgentService`, `com.oryxos.core.service.impl.AgentServiceImpl`
  - `com.oryxos.core.context.ProfileContext`
  - `com.oryxos.core.context.ContextLoader`, `com.oryxos.core.context.impl.ContextLoaderImpl`
- `oryxos-storage`:
  - `com.oryxos.storage.entity.ToolInvocationEntity`
  - `com.oryxos.storage.repository.ToolInvocationRepository`
  - `src/main/resources/schema.sql` (确认包含 `tool_invocations`)
- Tests:
  - `ReActLoopTest` (oryxos-core)
  - `PromptBuilderTest` (oryxos-core)
  - `ToolExecutorTest` (oryxos-core)
  - `AgentServiceTest` (oryxos-core)
  - `ContextLoaderTest` (oryxos-core)
  - `ToolInvocationRepositoryTest` (oryxos-storage)

## Test Strategy

- 5 项课件验收 Harness 测试：
  1. `ReActLoopTest`: 单轮直接收尾、多轮工具调度回填、最大轮数（10轮）强制停兜底、会话消息累积。
  2. `PromptBuilderTest`: 四段式顺序拼装、超 20 轮历史截断、System Prompt 末尾注入当前日期时间。
  3. `ToolExecutorTest`: 成功记录 `success=true`、失败记录 `success=false` 与 `error_message`，异常不吞。
  4. `AgentServiceTest`: `ProfileContext` 在处理期间可用、抛异常时 `finally` 清理、会话持久化。
  5. `ContextLoaderTest`: 实时无缓存读取、Skill 缺失报错、Bootstrap 缺失告警。
- 全量构建与规约门禁：`mvn clean verify` 100% 通过。
