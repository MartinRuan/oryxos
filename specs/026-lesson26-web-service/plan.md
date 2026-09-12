# Implementation Plan: Lesson 26 - Web Service and Read-Only Admin Console

**Branch**: `026-lesson26-web-service` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/026-lesson26-web-service/spec.md`

## Summary

在既有 OryxOS 引擎之上补齐同步 REST 门面和只读管理平台。六个资源 Controller 提供经用户澄清后确定的 11 个核心端点：五项会话能力、一次性 Agent 调用、Profile/Memory/Tool 查询以及 health/info。Controller 只负责校验、DTO 映射与响应包装，所有执行委托给既有 `AgentService`、`SessionManager`、`ProfileRegistry`、`MemoryService`、`ToolRegistry` 和 `ProviderRegistry`。成功与错误沿用第 24 节已有 `ApiResponse`；Agent 调用由虚拟线程执行器施加 60 秒阻塞等待上限；静态管理页直接消费公开查询端点。

## Technical Context

- **固定技术栈**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **Language/Version**: Java 21；避免启用 preview features。
- **Primary Dependencies**: Spring Boot 3.3.5、Spring MVC、Spring `AsyncTaskExecutor`、Jakarta Validation、springdoc-openapi 2.6.0；复用 `oryxos-core`、`oryxos-provider`、`oryxos-memory`、`oryxos-tool`、`oryxos-storage`。已执行 `./mvnw -pl oryxos-web dependency:tree`，现有 Spring MVC、Validation、springdoc 依赖均由 BOM 锁定；本地 `javap` 已确认 `AsyncTaskExecutor.submit(Callable)` 和 Boot `applicationTaskExecutor` Bean 常量存在；只需补内部模块直接依赖，不新增第三方依赖。
- **Storage**: 复用 SQLite `sessions` 表和 `.oryxos/memory/MEMORY.md`；本节不新增表、不改 DDL。
- **Testing**: JUnit 5、Mockito、AssertJ、Spring MockMvc、`@WebMvcTest`、`@SpringBootTest`。
- **Target Platform**: Linux/Kubernetes 或服务器上的单体 fat JAR；浏览器访问同一服务托管的静态管理页。
- **Project Type**: Maven 多模块单体 Web Service + 静态前端。
- **Performance Goals**: 200 个并发一次性调用可被单实例接收；单次 Agent HTTP 调用最多等待 60 秒；消息最大 32KB；会话详情最多返回最近 100 条消息。
- **Constraints**: Spring MVC + 同步阻塞；启用虚拟线程；禁止 WebFlux、Reactor、`CompletableFuture`、自建线程池、SSE、WebSocket、认证、RBAC、限流；Controller 保持薄层；500 响应不得泄露内部异常；Provider DTO 不得序列化 API key。
- **Scale/Scope**: 六个 Controller、11 个核心端点、一个五导航只读管理页；会话集合核心阶段不分页，按最后活动时间倒序返回摘要。
- **模块落位**: Controller、DTO、异常处理、CORS 配置、60 秒调用包装与 `static/admin` → `oryxos-web`；60 秒调用包装注入 Boot 自动配置的 `applicationTaskExecutor`，不声明线程池；`SessionManager.list()` 契约 → `oryxos-core`，持久化实现 → `oryxos-storage`；`MemoryService.load()` 契约 → `oryxos-core`，实现 → `oryxos-memory`；真实上下文冒烟测试 → `oryxos-boot`。
- **测试策略**: `测试策略按课件"验收 harness"执行：SessionApiControllerTest、GlobalExceptionHandlerTest、WebSmokeIT（覆盖消息超过 32KB 返回 400、Session 不存在返回 404、正常请求 AgentService.process 恰调用一次、各类异常状态码与统一 ApiResponse 四字段、500 不泄漏内部消息、/health /info /profiles /tools 真实链路可达），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **语法禁区**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

*GATE: Phase 0 前与 Phase 1 后均通过。*

1. **单二进制与自包含部署**：静态管理页打入 `oryxos-web` resources 并随现有 fat JAR 发布；不增加外部数据库、缓存、队列或前端构建服务。
2. **自实现 ReAct Loop**：所有 Web 消息只调用 `AgentService.process`，不复制或旁路 `ReActLoop`。
3. **Spring AI 职责限定**：本节不使用 Spring AI Agent 抽象或自动 Tool 执行。
4. **Provider 显式映射**：`SystemApiController` 只读取 `ProviderRegistry` 的显式描述与可用状态，不按 Bean 类型扫描。
5. **一个目录一个 Agent**：Web 核心端点仅调用已存在 Agent；不创建、编辑或删除 Agent 目录。
6. **Day One 审计与可观测**：Agent 请求复用既有审计链路；日志使用 SLF4J；health/info 与既有 Actuator 并存。
7. **应用层白名单沙箱**：Web 层不直接执行 Tool，不绕过 `ToolExecutor` 与 `Sandbox`。
8. **同步阻塞与虚拟线程**：HTTP 保持同步返回；60 秒边界复用 Boot 自动配置的虚拟线程 `applicationTaskExecutor`，使用 JDK `Future.get(timeout)` 阻塞等待，不引入 Reactor、WebFlux、`CompletableFuture` 或自建线程池。
9. **Tool 三合一**：Web 仅从 `oryxos-tool` 的 `ToolRegistry` 读取元数据，不移动 Tool/MCP/Sandbox 实现。
10. **质量与 TDD**：先补 harness 与受影响接口测试，再实现；最终执行 `./mvnw clean verify` 和语法/全局不变量检查。

### Post-Design Re-check

- 所有未知项已在 [research.md](research.md) 收敛，无 `NEEDS CLARIFICATION`。
- API 契约不暴露凭证或内部异常；没有新增数据库表和第三方依赖。
- `SessionManager.list()` 与 `MemoryService.load()` 是用户选择方案 A 和既有查询端点所需的最小兼容扩展，两种 SessionManager 实现与 MemoryService 实现均纳入测试任务。
- Constitution 十项原则全部通过，无需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/026-lesson26-web-service/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── rest-api.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/com/oryxos/
├── core/session/SessionManager.java                 # 增加只读会话列表契约
└── memory/MemoryService.java                        # 增加长期记忆只读加载契约

oryxos-storage/src/main/java/com/oryxos/storage/
├── repository/SessionRepository.java                # 最后活动时间倒序查询
└── session/JpaSessionManager.java                   # 持久化 list 实现

oryxos-memory/src/main/java/com/oryxos/memory/
└── impl/MemoryServiceImpl.java                      # load 委托 LongTermMemory

oryxos-web/src/main/
├── java/com/oryxos/web/
│   ├── common/ApiResponse.java                      # 复用既有四字段信封
│   ├── config/WebConfig.java                        # 核心阶段 CORS
│   ├── controller/
│   │   ├── SessionApiController.java
│   │   ├── AgentApiController.java
│   │   ├── ProfileApiController.java
│   │   ├── MemoryApiController.java
│   │   ├── ToolApiController.java
│   │   └── SystemApiController.java
│   ├── dto/                                         # 请求与安全只读响应 DTO
│   ├── exception/
│   │   ├── AgentInvocationTimeoutException.java
│   │   └── GlobalExceptionHandler.java
│   └── service/AgentInvocationRunner.java           # 同步 60 秒调用边界
└── resources/static/admin/
    ├── index.html
    ├── app.js
    └── styles.css

oryxos-web/src/test/java/com/oryxos/web/
├── controller/SessionApiControllerTest.java
└── exception/GlobalExceptionHandlerTest.java

oryxos-boot/src/
├── main/resources/application.yaml                  # 8080、virtual thread、swagger 路径
└── test/java/com/oryxos/boot/WebSmokeIT.java        # @Tag("integration") 真实上下文
```

**Structure Decision**: Web 适配器、DTO、超时边界和静态资源均收敛在 `oryxos-web`；领域契约留在 `oryxos-core`，下游模块实现依赖倒置；`oryxos-boot` 仅承载必须扫描全模块的真实上下文冒烟测试。

## Complexity Tracking

无 Constitution 违规。新增 `AgentInvocationRunner` 是为同时满足 60 秒端到端等待上限、同步 MVC 和薄 Controller 的最小 Web 应用服务；它复用 Boot 自动配置的 `applicationTaskExecutor`，不创建线程池，也不形成新的业务能力域。
