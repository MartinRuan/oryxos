# Implementation Plan: 动态管理 Agent

**Branch**: `030-lesson30-dynamic-agent-management` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/030-lesson30-dynamic-agent-management/spec.md`

## Summary

把 `.oryxos/agents/<name>/` 维持为 Agent 唯一真相源，在 `oryxos-core` 增加目录存储、生命周期编排和实时监听：API 写目录、手工目录事件与启动扫描最终都调用同一个 `register(Path)`，复用第 29 节的 `AgentLoader`、`ProfileRegistry` 与 `AgentScheduler`。在 `oryxos-web` 扩展 Agent CRUD/生成接口和只读工作区浏览接口，并升级现有原生管理页面。草稿生成经可配置的 `minimax / MiniMax-M2.7` 默认路由调用既有 `ProviderService`，继续写入 `llm_calls` 审计。长期记忆继续使用单个 `MEMORY.md`，由 `ProfileContext` 为新条目标记 Agent，读取时合并该 Agent 专属条目与历史共享条目；Agent 详情增加记忆 Tab，OS运行时只保留 Provider、Tool 与 Sandbox 白名单。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5、Spring Framework 6.1.14、Spring AI Alibaba 1.0.0-M2（仅由既有 `ProviderService` 做协议转换）、SnakeYAML 2.3、SLF4J、JDK `WatchService`；不新增第三方依赖

**Storage**: `.oryxos/agents/` 活动 Agent 目录、`.oryxos/archive/` 归档目录、带 `[agent:<name>]` 兼容标记的既有 `.oryxos/memory/MEMORY.md`；复用 SQLite 中既有 `llm_calls`、`sessions` 与 scheduled task 状态，不新增数据库表

**Testing**: JUnit 5、AssertJ、Mockito、Spring MockMvc；Maven Surefire 与现有 Spotless、Checkstyle、P3C/PMD、SpotBugs/FindSecBugs、OWASP 门禁

**Target Platform**: Linux 服务器/K8s，JDK 21，单实例核心阶段

**Project Type**: Maven 多模块 Spring Boot 单体 Web 应用，fat JAR 分发，原生 HTML/CSS/JavaScript 管理端

**Performance Goals**: Agent 目录新增/修改/移走的 95% 在 5 秒内反映到运行时；非 LLM 的管理与文件浏览操作在本地工作区正常规模下 1 秒内完成；100 个 Agent 的启动全量扫描保持可接受

**Constraints**: 同步阻塞请求链路 + 虚拟线程；只允许一个明确生命周期的 WatchService 守护线程，不引入 Reactor、`CompletableFuture` 或自建线程池；文件路径必须限制在 `.oryxos/`；密钥仅从环境变量进入现有 Provider 配置；JSON create 只写 `AGENT.md`；不新增依赖或数据表；避开 P3C/ASM 不支持的 Java 18+ 语法形态

**Scale/Scope**: 单实例最多约 100 个 Agent 目录；管理端新增 Agent 管理与工作区两个页面区域，详情包含基本信息、文件、会话、记忆；Agent API 覆盖生成、创建、列表、详情、更新、删除和既有 invoke，工作区 API 覆盖 tree/file，既有 Memory API 增加可选 agent 查询参数

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **单二进制与自包含部署**：PASS。只使用 JDK WatchService、本地目录与现有 SQLite，不增加外部基础设施。
- **自实现 ReAct 循环**：PASS。Agent 创建/生成不改变 ReActLoop；正式调用仍走既有 AgentService。
- **Spring AI 职责边界**：PASS。草稿生成只经 ProviderService 做协议转换，不启用自动工具执行。
- **Provider 显式映射**：PASS。生成专用配置显式指定 `minimax` 和 `MiniMax-M2.7`，通过 ProviderService 路由。
- **一个目录 = 一个 Agent**：PASS。AgentStore 只管理完整 Agent 目录，API、监听与启动扫描汇入同一注册方法。
- **审计与可观测**：PASS。草稿生成经 ProviderService，无论成败写 `llm_calls`；异常不吞，监听器记录单目录失败。
- **应用层沙箱**：PASS。工作区读写使用规范化路径和真实路径边界校验；文件浏览拒绝目录穿越与符号链接越界。
- **同步阻塞与虚拟线程**：PASS。REST 与生命周期调用同步；WatchService 守护线程是课件明确的基础设施线程。
- **Tool 三合一**：PASS。本功能不新增 Tool，不移动 Sandbox/MCP/内置 Tool。
- **质量门禁与 TDD**：PASS。按课件 harness 先写测试，最终执行 `mvn clean verify`。

## Project Structure

### Documentation (this feature)

```text
specs/030-lesson30-dynamic-agent-management/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── agent-management-api.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/core/
├── config/
│   ├── AgentGenerationProperties.java       # 新增生成 Provider/model 配置
│   └── CoreAutoConfiguration.java           # 装配生命周期、Watcher 与启动顺序
├── model/
│   └── AgentView.java                       # Agent 安全定义视图
├── profile/
│   ├── AgentLifecycleService.java           # create/register/get/list/update/delete/generate 编排
│   ├── AgentStore.java                      # AGENT.md 原子写入、读取、回滚、归档与根路径约束
│   ├── WorkspaceWatcher.java                # 启动全量扫描 + WatchService 实时监听
│   └── AgentLoader.java                     # 复用 deriveProfile，启动入口改由 Lifecycle 汇合
└── scheduler/
    └── AgentScheduler.java                  # 新增 unregisterProfile

oryxos-core/src/test/java/com/oryxos/core/
├── profile/
│   ├── AgentLifecycleServiceTest.java
│   ├── GenerateTest.java
│   └── WorkspaceWatcherTest.java
└── scheduler/
    └── AgentSchedulerRegisterTest.java       # 增补注销句柄回归

oryxos-web/src/main/java/com/oryxos/web/
├── controller/
│   ├── AgentApiController.java              # 扩展 generate/CRUD/list，保留 invoke
│   └── WorkspaceApiController.java          # tree/file 只读接口
└── dto/
    ├── GenerateAgentRequest.java
    ├── CreateAgentRequest.java
    ├── UpdateAgentRequest.java
    └── FileNode.java

oryxos-web/src/test/java/com/oryxos/web/controller/
├── AgentApiControllerTest.java
└── WorkspaceApiControllerTest.java

oryxos-web/src/main/resources/static/admin/
├── index.html                               # Agent 管理/工作区页面结构
├── app.js                                   # 生成预览、CRUD、目录树与文件读取
└── styles.css                               # 表单、弹窗、树与文件预览样式

oryxos-boot/src/main/resources/application.yaml # oryxos.agent-generation.provider/model

.oryxos/archive/.gitkeep
```

**Structure Decision**: 生命周期、目录真相源与监听属于核心 Agent 运行域，落在 `oryxos-core`；REST DTO、控制器和静态管理页面留在 `oryxos-web`；`oryxos-boot` 只提供默认生成配置。跨模块只复用既有 `Profile`、`ProviderService`、`ProfileRegistry`、`AgentLoader` 与 `AgentScheduler`，不引入反向依赖。

## Phase 0 Research Decisions

详见 [research.md](./research.md)。核心决策包括：目录原子写与唯一归档名、幂等注册吸收 API/Watcher 重复事件、WatchService 的根目录与 Agent 子目录双层监听、生成 Provider 的显式配置、基于真实路径的文件浏览边界，以及更新失败时恢复原定义和运行时状态。

## Phase 1 Design

- 领域状态与生命周期见 [data-model.md](./data-model.md)。
- REST 请求、响应和错误契约见 [contracts/agent-management-api.md](./contracts/agent-management-api.md)。
- 端到端验证步骤见 [quickstart.md](./quickstart.md)。

## Post-Design Constitution Re-check

Phase 1 未引入新模块、第三方依赖、数据库表、自动 Tool 执行或异步请求模型。新增对外概念全部来自课件交付物；新增 `oryxos.agent-generation.provider/model` 已经用户在 clarify 阶段明确选择。文件读写与目录监听均限定在 `.oryxos/`，设计仍满足全部十条宪章门禁。

## Test Strategy

测试策略按课件“验收 harness”执行：`AgentLifecycleServiceTest`、`WorkspaceWatcherTest`、`WorkspaceApiControllerTest`、`AgentApiControllerTest`、`GenerateTest`，覆盖创建顺序与回滚、API/Watcher/启动扫描共用注册路径、目录实时增改删、删除顺序、更新 schedule 先注销后注册、生成只返回不落盘、工作区目录穿越与符号链接越界拒绝，以及统一错误信封。相关实现测试先行；单测默认运行，若增加端到端冒烟则标记 `@Tag("integration")` 供 CI 默认跳过。完成定义是 `./mvnw clean verify` 全绿。

## Complexity Tracking

无宪章违规项，不需要例外说明。
