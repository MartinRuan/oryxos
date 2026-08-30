# Implementation Plan: US-1 LLM Provider 对接与显式路由

**Branch**: `001-llm-provider-integration` | **Date**: 2026-08-29 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/001-llm-provider-integration/spec.md)

**Input**: Feature specification from `/specs/001-llm-provider-integration/spec.md`

## Summary

实现 OryxOS 的 LLM Provider 统一门面抽象（`ProviderService`），建立 `provider.name -> ChatModel` 的显式映射管理机制，支持主流云端模型（DeepSeek、Qwen、Kimi、OpenAI）、企业本地部署模型（Ollama/vLLM）以及内置测试专用的 `mock` Provider。基于 Spring AI 仅做多协议抹平与 Function Calling Schema 生成，显式禁用框架层自动工具调用，每次调用实时落库 `llm_calls` 审计数据，并支持 `${ENV_VAR}` 安全密钥注入。

---

## Technical Context

**Language/Version**: Java 21 (LTS) / 兼容 JDK 17+ 语法，启用 Virtual Threads 并发执行。

**Primary Dependencies**: Spring Boot 3.3.5, Spring AI Alibaba 1.0.0-M2 (协议转换与 Schema 生成), SQLite JDBC 3.47.1.0, Spring Data JPA, Logback + Logstash Logback Encoder 8.0。

**Storage**: SQLite 嵌入式数据库 (`.oryxos/oryxos.db`)，`llm_calls` 审计表 Day One 同步写入。

**Testing**: JUnit 5 (Jupiter), AssertJ, Mockito, 内置脱机 `MockChatModel` 实现 100% 离线快速自动化验证。

**Target Platform**: Linux / Windows / macOS 服务器与 Docker / Kubernetes 单二进制 fat JAR。

**Project Type**: Java 多模块后端单体底座（`oryxos-core`, `oryxos-provider`, `oryxos-storage`）。

**Performance Goals**: 单实例支持 500+ 并发路由调度，Provider 路由及协议转换额外开销 < 2 毫秒。

**Constraints**: 同步阻塞模型（原则八），禁用 Spring AI 自动 Tool 执行（原则二），显式 Provider 映射（原则三），全链路审计（原则六），无明文硬编码密钥（原则十）。

**Scale/Scope**: 覆盖 5+ 主流 Provider 驱动适配（DeepSeek, Qwen, Kimi, OpenAI, Ollama/vLLM, Mock），支持 100% 离线 CI 构建。

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪章核心原则 | 检查要求 | 本方案符合性判定 |
| :--- | :--- | :--- |
| **一、单二进制与自包含部署** | 默认仅依赖内置 SQLite 与本地文件，无外部 MQ/Redis 硬依赖 | **PASS** - 仅使用 SQLite `llm_calls` 与内存映射 |
| **二、自实现 ReAct 循环** | 严禁使用 Spring AI 的 Agent/Client 自动工具执行链 | **PASS** - ProviderService 仅返回 `ToolCallIntent`，不执行工具 |
| **三、Spring AI 严格限定两项职责** | 仅用协议转换与 Schema 生成，显式禁用自动执行 | **PASS** - 调用底层 `ChatModel.call(Prompt)`，无自动工具拦截 |
| **四、Provider 必须显式映射** | 维护显式 `provider.name -> ChatModel` 映射，严禁按 Bean 类型扫描 | **PASS** - 通过 `ProviderRegistry` 显式维护 `Map<String, ChatModel>` |
| **六、审计表 Day One 写入** | 每次 LLM 调用必须记录 Token 与耗时落库 `llm_calls` | **PASS** - 模板方法内切面同步持久化 `LlmCallAudit` |
| **八、同步阻塞执行模型与虚拟线程** | 全程同步阻塞模型，开启 Virtual Threads，无 WebFlux/Reactor | **PASS** - 纯同步接口 `call(ChatRequest)` |
| **十、严格质量门禁与 TDD 验证** | 100% 通过 Spotless、阿里 P3C、Checkstyle、SpotBugs、内置 Mock | **PASS** - 内置 `MockChatModel` 保证 CI 离线全绿通过 |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-llm-provider-integration/
├── spec.md              # Feature specification (Clarified & Ready)
├── plan.md              # Technical implementation plan
├── research.md          # Phase 0 decisions & alternatives
├── data-model.md        # Phase 1 domain entities & value objects
├── quickstart.md        # Phase 1 quickstart & verification guide
├── contracts/           # Phase 1 interface contracts
│   └── provider-service-contract.md
└── checklists/
    └── requirements.md  # 16/16 quality checks passed
```

### Source Code Impact (Submodules)

```text
oryxos-core/
└── src/main/java/com/oryxos/core/
    ├── model/
    │   ├── ProviderDescriptor.java
    │   ├── ChatRequest.java
    │   ├── ChatResponse.java
    │   ├── ChatMessage.java
    │   ├── MessageType.java
    │   ├── ToolDefinition.java
    │   ├── ToolCallIntent.java
    │   ├── FinishReason.java
    │   └── TokenUsage.java
    └── OryxTool.java

oryxos-provider/
└── src/main/java/com/oryxos/provider/
    ├── ProviderService.java
    ├── ProviderRegistry.java
    ├── config/
    │   ├── ProviderProperties.java
    │   └── ProviderAutoConfiguration.java
    ├── exception/
    │   ├── ProviderException.java
    │   └── ProviderErrorCode.java
    ├── adapter/
    │   ├── FunctionCallingAdapter.java
    │   └── ChatModelFactory.java
    ├── mock/
    │   └── MockChatModel.java
    └── impl/
        └── DefaultProviderService.java

oryxos-storage/
└── src/main/java/com/oryxos/storage/
    ├── entity/
    │   └── LlmCallEntity.java
    └── repository/
        └── LlmCallRepository.java
```

---

## Complexity Tracking

> *无宪章违反项。架构设计完全遵循技术宪章与技术方案。*
