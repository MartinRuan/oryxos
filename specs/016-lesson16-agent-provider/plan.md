# Implementation Plan: 第16节 Agent Provider 与 Profile 对接与显式路由

**Branch**: `016-lesson16-provider` | **Date**: 2026-08-29 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/016-lesson16-agent-provider/spec.md)

**Input**: Feature specification from `specs/016-lesson16-agent-provider/spec.md`

## Summary

实现 OryxOS 的第一块核心能力 Provider 与 Profile 配置体系：
1. 在 `oryxos-core` 实现 `Profile` 领域实体（承载全字段元数据）、`ProfileLoader`（基于 SnakeYAML 扫描解析 `.oryxos/profiles/*.yaml`，支持 `${ENV_VAR}` 占位解析与校验）以及 `ProfileRegistry`（内存快速查找与注册）；
2. 在 `oryxos-provider` 实现统一 `ProviderService`，建立 `provider.name -> ChatModel` 的显式映射表，基于 Spring AI 做多协议抹平与 Function Calling Schema 转换（`ToolSchemaAdapter`），显式关闭框架层自动工具执行（`autoExecuteTools=false`）；
3. 在 `oryxos-storage` 实现 `LlmCallEntity`（`LlmCall`）、`LlmCallRepository` 与 SQLite 手工建表脚本（含 `success`/`error_message`），实现每次 LLM 调用成败均实时落库审计；
4. 全面落地课件验收 Harness 测试套件（`ProfileLoaderTest`, `ProviderServiceTest`, `ToolSchemaAdapterTest`, `LlmCallRepositoryTest`, `ProviderSmokeIT`）。

---

## Technical Context

**Language/Version**: Java 21 (LTS) / 兼容 JDK 17+ 语法，启用 Virtual Threads 并发执行。避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 `default ->` 写法），静态检查是构建门禁。

**Primary Dependencies**: JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 `mvn dependency:tree` 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA、SnakeYAML。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 `hibernate.ddl-auto=update`。

**Module Placement (模块落位)**:
- `Profile` / `ProfileLoader` / `ProfileRegistry` → `oryxos-core`
- `ProviderService` / 工具适配器 (`ToolSchemaAdapter`) / 显式映射与 Mock → `oryxos-provider`
- `LlmCall` (`LlmCallEntity`) + `LlmCallRepository` + 手工建表脚本 → `oryxos-storage`

**Testing Strategy (测试策略)**:
测试策略按课件"验收 harness"执行：`ProfileLoaderTest`, `ProviderServiceTest`, `ToolSchemaAdapterTest`, `LlmCallRepositoryTest`, `ProviderSmokeIT`（覆盖 按名路由不串台、未知名抛异常、失败留审计、关自动执行、schema 翻译无执行逻辑、手工脚本建表验证、真 Key 连通性），单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成的定义是 `mvn clean verify` 全绿。

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪章核心原则 | 检查要求 | 本方案符合性判定 |
| :--- | :--- | :--- |
| **一、自实现 ReAct Loop** | 严禁使用 Spring AI 的 Agent/Client 自动工具执行链 | **PASS** - Provider 仅负责协议转换与 schema 翻译，执行权完全由 ReActLoop 掌控 |
| **二、Spring AI 严格限定两项职责** | 仅用协议转换与 Schema 生成，显式禁用自动执行 | **PASS** - 底层调用 `autoExecuteTools=false`，`ToolSchemaAdapter` 只翻译不执行 |
| **三、Provider 必须显式映射** | 维护显式 `provider.name -> ChatModel` 映射，严禁按 Bean 类型扫描 | **PASS** - 由 `ProviderRegistry` / `ProviderProperties` 显式维护映射表 |
| **四、一个目录 = 一个 Agent** | Profile 派生自配置，Skill 渐进式披露 | **PASS** - Profile 包含全字段，ProfileLoader 提供全字段解析支持 |
| **五、审计表 Day One 写入** | 每次 LLM 调用必须记录 Token 与耗时落库 `llm_calls` | **PASS** - `llm_calls` 表（含 `success`/`error_message`）在每次调用同步写入 |
| **七、同步阻塞执行模型** | 全程同步阻塞模型，配合 Java 21 虚拟线程，无响应式编程 | **PASS** - 纯同步阻塞 API，无 Reactor/CompletableFuture |

---

## Project Structure

### Documentation (this feature)

```text
specs/016-lesson16-agent-provider/
├── spec.md              # Feature specification (Clarified & Ready)
├── plan.md              # Technical implementation plan
├── research.md          # Phase 0 decisions & alternatives
├── data-model.md        # Phase 1 domain entities & value objects
├── quickstart.md        # Phase 1 quickstart & verification guide
├── contracts/           # Phase 1 interface contracts
│   └── provider-and-profile-contracts.md
└── checklists/
    └── requirements.md  # Quality checklist
```

### Source Code Impact (Submodules)

```text
oryxos-core/
└── src/main/java/com/oryxos/core/
    ├── model/
    │   ├── Profile.java            # Agent 配置实体（全字段）
    │   ├── ChatRequest.java
    │   ├── ChatResponse.java
    │   ├── ChatMessage.java
    │   ├── TokenUsage.java
    │   └── ...
    ├── profile/
    │   ├── ProfileLoader.java      # 扫描解析 YAML 与校验
    │   └── ProfileRegistry.java    # 内存索引
    └── OryxTool.java

oryxos-provider/
└── src/main/java/com/oryxos/provider/
    ├── ProviderService.java        # 统一 Provider 门面契约 (含 chat / call)
    ├── ProviderRegistry.java       # provider name -> ChatModel 显式映射
    ├── adapter/
    │   ├── ToolSchemaAdapter.java  # OryxTool schema 转 Spring AI 格式（只翻译不执行）
    │   └── ChatModelFactory.java
    ├── mock/
    │   └── MockChatModel.java      # 脱机测试 Mock 模型
    └── impl/
        └── ProviderServiceImpl.java # 核心路由、关自动执行、成败落库

oryxos-storage/
└── src/main/java/com/oryxos/storage/
    ├── entity/
    │   └── LlmCallEntity.java      # llm_calls 实体（含 success, errorMessage）
    └── repository/
        └── LlmCallRepository.java  # Spring Data JPA 存储接口
```
