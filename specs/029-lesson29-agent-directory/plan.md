# Implementation Plan: 一个目录定义一个会自己运行的 Agent

**Branch**: `029-lesson29-agent-directory` | **Date**: 2026-09-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/029-lesson29-agent-directory/spec.md`

## Summary

新增 `AgentLoader`，把 `.oryxos/agents/<name>/AGENT.md` 的 frontmatter 派生成既有 `Profile`，把正文与私有资源位置保留为 Agent 目录语义，并复用 `ProfileRegistry`、`AgentScheduler`、`ContextLoader` 和既有 ReAct 执行链。启动扫描和运行时加载走同一条注册路径；ContextLoader 每轮只重读并注入去掉 frontmatter 的主正文，`skills/*.md`、`REFERENCE.md` 与 `scripts/*` 由已有 `read_file`/`shell` 按需访问。示例交付 `.oryxos/agents/daily-reconcile/`。

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.3.5、Spring Framework 6.1.14、SnakeYAML 2.3、SLF4J；不新增第三方依赖
**Storage**: 工作区文件系统；复用既有进程内 `ConcurrentHashMap` 与调度状态存储，不新增数据库表
**Testing**: JUnit 5、AssertJ、Mockito、Spring Boot Test；Maven Surefire/Failsafe 及现有质量插件
**Target Platform**: Linux 服务器/K8s，JDK 21
**Project Type**: Maven 多模块 Spring Boot 单体
**Performance Goals**: 100 个 Agent 目录的本地启动扫描在 1 秒内完成；上下文加载只读取当前 Agent 主文件
**Constraints**: 同步阻塞模型与虚拟线程；无文件监听；无新 REST API；凭证仅允许环境变量占位；避免 P3C/ASM 不支持的 Java 语法形态
**Scale/Scope**: 一个工作区、单进程注册中心、直接子目录级扫描；本节六个 harness 类和一个四文件示例 Agent

依赖核验：`./mvnw -pl oryxos-core dependency:tree -Dincludes=org.yaml:snakeyaml,org.springframework:spring-context,org.springframework:spring-core` 已通过，确认 SnakeYAML 2.3 与 Spring 6.1.14 均来自现有依赖树。

## Constitution Check

*GATE: Phase 0 前检查，并在 Phase 1 后复核。*

| 原则 | 结论 | 设计约束 |
|---|---|---|
| 自实现 ReAct Loop | PASS | 不修改模型调用与工具循环，目录 Agent 仍进入 `AgentService.process`。 |
| Spring AI 限定职责 | PASS | 不新增 Spring AI Agent 或自动工具执行。 |
| Provider 显式映射 | PASS | 校验只读取 `ProviderService` 已显式注册的 Provider。 |
| 一个目录 = 一个 Agent | PASS（用户裁决） | 以修改后的第 29 节为准：Skill 是 Agent 私有文件，不采用宪章旧文中的公共 Skill、全局索引和软链接。该冲突已由用户明确裁决。 |
| 审计 Day One | PASS | 执行继续复用已有 LLM/Tool 审计链，不绕开 `AgentService`。 |
| 应用层白名单沙箱 | PASS | 文件与脚本访问复用 Sandbox；解释器脚本参数限制在当前工作区 `.oryxos/agents/<name>/scripts/` 形态，且不承诺子进程网络隔离。 |
| 同步阻塞执行 | PASS | 文件扫描、读取、注册、调度均同步执行。 |
| Tool 模块三合一 | PASS | 不把 Agent 目录或私有资源注册为 Tool；必要的 shell 边界仍落在 `oryxos-tool`。 |
| 模块依赖方向 | PASS | `AgentLoader` 与运行时注册方法位于 `oryxos-core`；无新增模块、无循环依赖。 |

Phase 1 复核：接口契约未增加 REST 路径、Profile 字段、配置键、数据库表或第三方依赖。对外新增面仅限课件点名的 `AgentLoader`、`deriveProfile(Path)`、`ProfileRegistry.remove/exists` 与 `AgentScheduler.registerProfile`。

## Design

### 目录解析与注册

- `AgentLoader` 只扫描给定根目录的直接子目录；每个候选目录必须存在 `AGENT.md`。
- 加载时拆分 frontmatter 和正文，识别 `skills/`、`scripts/`、`REFERENCE.md`，再委托现有 `ProfileLoader` 做 YAML 映射与环境变量解析。
- `deriveProfile(Path)` 校验 `name == 目录名`，并完整保留 identity、provider、tools、notify_channels、schedules、bootstrap 与 settings。
- 单目录注册顺序固定为：完整解析与校验 → `ProfileRegistry.register` → `AgentScheduler.registerProfile`。失败前不得写入注册中心或调度器。
- 扫描时单个坏目录记录带路径的错误并继续处理其他目录；返回值仅包含完整注册成功的 Agent。
- 未知工具由注册校验记录 WARN，包含 Agent 名和工具名；未知工具不会被隐式注册。

### 同源校验

- `ProfileRegistry.register(Profile)` 是启动加载和运行时加载的共同写入口。
- 必填 `name`、`provider`、`provider.name` 使用与 `ProfileLoader` 相同的错误类型和消息。
- Spring 装配时向注册校验提供已有 `ProviderService` 与 `OryxTool` 集合；可用 Provider 非空时拒绝未知 Provider，工具集合可用时对未知工具告警。
- 保留 `containsProfile` 兼容既有调用，并新增课件要求的 `exists` 与 `remove`；第 30 节可在停止调度后移除注册项。

### 渐进式披露

- `ContextLoaderImpl` 根据 `profile.name` 定位 `.oryxos/agents/<name>/AGENT.md`，每次调用重新读取并只注入正文。
- frontmatter、`skills/*.md`、`REFERENCE.md` 和 `scripts/*` 内容不进入默认上下文。
- 为避免破坏第 16～28 节手写 Profile，找不到目录型 Agent 主文件时保留原 Bootstrap/legacy Skill 加载路径；目录型 Agent 命中后采用第 29 节私有资源规则。
- 资源访问仍通过已有 `read_file`/`shell`，因此继续经过工具审计与 Sandbox。

### 调度

- `AgentScheduler.registerAll()` 复用 `registerProfile(Profile)`，不再拥有另一份循环实现。
- `registerProfile` 先协调该 Agent 的持久化任务状态，再为启用任务创建/替换 `ScheduledFuture<?>` 句柄。
- 句柄仍以 schedule id 为键保存在既有并发 Map；仅提供包内只读测试缝隙，不扩大公共 API。
- 重复注册同一 Profile 时先取消该 Profile 旧句柄后重建，避免并存重复触发；完整更新/冲突策略仍留在第 30 节。

### 示例与脚本边界

- 按课件原文创建 `daily-reconcile` 的 `AGENT.md`、`skills/report-format.md`、`scripts/reconcile.py`、`REFERENCE.md`。
- 示例不写明文数据库或 webhook 凭证，全部使用环境变量。
- `shell` 继续要求命令白名单；当白名单解释器用于执行 Agent 脚本时，脚本路径还必须规范化后位于 `.oryxos/agents/<name>/scripts/`，拒绝 `-c` 等绕开脚本文件路径的形式。

## Project Structure

### Documentation (this feature)

```text
specs/029-lesson29-agent-directory/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── agent-directory-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/
├── src/main/java/com/oryxos/core/
│   ├── config/CoreAutoConfiguration.java
│   ├── context/impl/ContextLoaderImpl.java
│   ├── profile/
│   │   ├── AgentLoader.java
│   │   ├── ProfileLoader.java
│   │   └── ProfileRegistry.java
│   └── scheduler/AgentScheduler.java
└── src/test/java/com/oryxos/core/
    ├── context/ProgressiveDisclosureTest.java
    ├── profile/
    │   ├── AgentLoaderTest.java
    │   ├── DeriveProfileTest.java
    │   ├── AgentScanRegisterTest.java
    │   └── ProfileRegistryRuntimeTest.java
    └── scheduler/AgentSchedulerRegisterTest.java

oryxos-tool/
├── src/main/java/com/oryxos/tool/builtin/ShellTools.java
└── src/test/java/com/oryxos/tool/builtin/ShellToolsTest.java

oryxos-boot/src/main/resources/application.yaml

.oryxos/agents/daily-reconcile/
├── AGENT.md
├── REFERENCE.md
├── skills/report-format.md
└── scripts/reconcile.py
```

**Structure Decision**: 在既有 Maven 模块内做增量修改。Agent 目录解析、注册和调度属于核心运行契约，放入 `oryxos-core`；进程执行边界留在已有 `oryxos-tool`；示例作为真实工作区资产放在 `.oryxos/agents/`。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 第 29 节私有 Skill 模型覆盖宪章 1.1.0 的公共 Skill + 软链接条款 | 用户明确要求以修改后的第 29 节为准；本节目标是自足 Agent 目录 | 同时保留两套模型会产生两种绑定语义，并违反课件明确的“无全局能力库/软链接”边界 |
