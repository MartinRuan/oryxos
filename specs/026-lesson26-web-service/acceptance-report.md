# 第 26 节 Web Service 验收报告

## 结论

- 分支：`026-lesson26-web-service`
- 自动化验收：通过
- 任务进度：T001–T034 完成；T035 保留为需要真实 Provider、故障注入和压测环境的人工验收
- 契约范围：按已确认的方案 A 暴露 6 个 Controller、11 个 REST 操作，其中新增 `GET /api/v1/sessions`

## 交付物核对

| 交付项 | 证据 | 结果 |
|---|---|---|
| Session API | `SessionApiController` 提供创建、列表、发消息、详情、归档 5 个操作 | 通过 |
| 一次性 Agent API | `AgentApiController` 提供 `/api/v1/agents/{name}/invoke` | 通过 |
| 只读能力 API | `ProfileApiController`、`MemoryApiController`、`ToolApiController` | 通过 |
| 系统 API | `SystemApiController` 提供 health 与安全 info | 通过 |
| 错误与超时 | `GlobalExceptionHandler`、`AgentInvocationRunner`、60 秒阻塞等待与 504 | 通过 |
| 管理台 | `static/admin/index.html`、`styles.css`、`app.js`，五个只读区域 | 通过 |
| OpenAPI | 11 个操作可从 `/v3/api-docs` 发现，Swagger UI 入口为 `/swagger-ui` | 通过 |
| 会话与记忆改造 | `SessionManager.list()`、JPA 倒序查询、`MemoryService.load()` | 通过 |

## Harness 对号

| 测试 | 覆盖结果 |
|---|---|
| `SessionApiControllerTest` | 五个会话操作、32KB+1 拒绝、404、最近 100 条、列表不泄漏正文、共享引擎恰调用一次 |
| `GlobalExceptionHandlerTest` | 400/404/500/503/504、统一四字段响应、500 不泄漏数据库连接串 |
| `WebSmokeIT` | 真实 Spring/JPA 上下文、6 个 Controller、11 个操作、六个只读路径、OpenAPI/Swagger、管理台静态资源 |
| `AgentInvocationRunnerTest` | 正常返回、60 秒超时取消、异常透传、中断标志恢复、Agent 调用恰一次 |
| `AgentApiControllerTest` | 成功回复、唯一临时会话、404、503、504、响应不暴露临时会话 ID |
| `SystemApiControllerTest` | Provider 可用状态、响应不包含 `apiKey` 或实际凭证 |
| Session/Memory 回归 | 内存与 JPA 列表排序、归档可查询、长期记忆加载委托 |

执行证据：

```text
./mvnw -pl oryxos-boot -am -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

./mvnw clean verify
12/12 Maven modules SUCCESS
BUILD SUCCESS
Total time: 44.219 s

./mvnw -pl oryxos-boot -am spring-boot:run
12/12 Maven modules SUCCESS; OryxApplication started on port 8080
GET /api/v1/health -> HTTP 200, code=0, status=UP
GET /admin -> HTTP 302, Location=/admin/index.html
```

完整门禁覆盖现有全项目测试，并执行 Checkstyle、Spotless、PMD、SpotBugs 与 Boot fat JAR repackage；`git diff --check` 通过。启动冒烟完成后已通过 SIGINT 优雅停机。

## H4 全局不变量

1. 本节 Web 代码没有新增文件、Shell、HTTP 等涉外 IO 实现；Controller 仅委托既有服务，因而没有绕过 `Sandbox.enforce` 的新路径。
2. LLM 与 Tool 仍走既有统一引擎；`LlmCallAuditIntegrationTest`、`LlmCallRepositoryTest`、`ToolExecutorTest`、`ToolInvocationRepositoryTest` 随全量回归通过，成功和失败审计契约未被绕开。
3. 明文凭证扫描只命中 `ProfileLoaderTest` 中的 `sk-test-key` 测试夹具，生产源码与配置没有明文 key。
4. 生产代码的会话 ID 拼接仍集中在 `SessionManager.generateSessionId`；Web 一次性调用把随机用户标识交给 `SessionManager` 生成会话 ID。
5. 禁区扫描未发现 WebFlux、Reactor、`CompletableFuture`、`ExecutorService` 或 `Executors.*`。Web 调用复用 Boot 的 `applicationTaskExecutor`，通过 `Future.get` 保持同步阻塞语义。
6. 生产源码未发现 Spring AI 自动 Tool 执行路径；Controller 委托既有 `AgentService.process`。

## 剩余人工验收（T035）

Harness 已判卷，以下项目需要在配置真实 Agent/Provider 的运行环境中人工执行：

- 按 `quickstart.md` 跑通 11 个 curl 请求，包含真模型消息调用，并核对 `llm_calls`、`tool_invocations` 审计记录。
- 用 CLI 产生会话，再通过 `GET /api/v1/sessions/{id}` 验证共享 SQLite 存储。
- 真实断开 Provider 验证 503；注入超过 60 秒的 Agent 调用验证 504。
- 对一次性 invoke 发起 200 并发请求，确认虚拟线程服务持续接受请求。
- 在浏览器核对管理台五个区域的真实数据和只读边界，并人工浏览 Swagger UI 文档。

复现命令与请求体见 [`quickstart.md`](quickstart.md)。
