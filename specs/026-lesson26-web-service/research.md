# Research: Lesson 26 - Web Service and Read-Only Admin Console

## 1. REST 门面与薄 Controller

- **Decision**: 六个 Controller 仅做输入校验、调用既有服务、DTO 映射和 `ApiResponse` 包装；Agent 执行统一进入 `AgentService.process`。
- **Rationale**: CLI、Scheduler 与 Web 必须共享同一 ReAct、Tool、Provider、Memory、Session 和审计链路，避免入口之间出现行为差异。
- **Alternatives considered**: 在 Controller 中拼装 Prompt 或直接调用 Provider；会绕过统一引擎与审计，拒绝。

## 2. 会话集合查询

- **Decision**: 按用户澄清的方案 A 新增 `GET /api/v1/sessions`，核心端点总数为 11。扩展 `SessionManager.list()`，内存和 JPA 实现均按 `lastActiveAt` 倒序返回；集合端点映射为不含消息正文的摘要，详情端点最多返回最近 100 条消息。
- **Rationale**: 管理平台需要真实会话列表；通过核心契约暴露查询可保持 Controller 薄，并让内存/SQLite 两种运行方式行为一致。
- **Alternatives considered**: Controller 直接注入 `SessionRepository` 会破坏依赖倒置；返回完整历史会造成无界响应；均拒绝。

## 3. 会话创建与一次性调用身份

- **Decision**: 创建会话请求要求 `profileName` 与 `userId`，渠道固定为 `web`，沿用既有 channel+user+profile 唯一会话规则；一次性 Agent 调用只要求 `content`，内部生成不可由客户端复用的临时会话 ID。
- **Rationale**: 复用第 18 节既有身份模型，同时让连续对话和一次性调用语义清楚。
- **Alternatives considered**: 无请求体随机选择 Profile 会造成不确定路由；允许客户端指定 channel 会污染入口身份；均拒绝。

## 4. 统一响应与 HTTP 状态

- **Decision**: 成功和错误继续使用 `ApiResponse(code, message, data, timestamp)`。全局异常处理返回 `ResponseEntity`，依据既有 `ErrorCode` 映射 400/404/500/503/504；兜底 500 只返回固定通用消息，完整异常仅写结构化日志。
- **Rationale**: 技术方案明确要求复用第 24 节响应信封，动态 HTTP 状态修正现有业务异常仍返回 200 的问题，并防止内部连接串或路径泄露。
- **Alternatives considered**: 新建 `ErrorBody` 会产生第二套错误协议；直接返回异常消息存在信息泄露；均拒绝。

## 5. 60 秒同步调用边界

- **Decision**: `AgentInvocationRunner` 注入 Spring Boot 自动配置的虚拟线程 `applicationTaskExecutor`，将 `AgentService.process` 提交后由当前请求线程使用 `Future.get(60, SECONDS)` 阻塞等待；超时取消任务并抛出统一 504 异常。本节不声明或创建线程池。
- **Rationale**: 对客户端仍是同步 REST，能够施加端到端等待上限，同时避免 WebFlux、Reactor、MVC async 返回类型、`CompletableFuture` 和自建线程池。已从本地锁定的 Spring 6.1.14 / Boot 3.3.5 类文件确认 `AsyncTaskExecutor.submit(Callable)` 与 `applicationTaskExecutor` Bean 常量存在。
- **Alternatives considered**: `spring.mvc.async.request-timeout` 对普通同步 Controller 不生效；Provider 单次 read timeout 无法覆盖多轮 ReAct；两者均不足。
- **Operational limit**: `Future.cancel(true)` 是尽力中断；若底层第三方客户端忽略中断，HTTP 会在 60 秒返回 504，但后台调用可能延后结束并完成审计。

## 6. 安全只读查询 DTO

- **Decision**: Profile、Provider、Tool、Session 均映射专用只读 DTO。Provider 只暴露名称、类型、默认模型、支持模型和可用状态；不暴露 API key。Tool 暴露名称、描述和输入 Schema。Memory 通过新增 `MemoryService.load()` 复用 `LongTermMemory.load()`。
- **Rationale**: 直接序列化领域对象会把 `ProviderDescriptor.apiKey` 或无关内部结构暴露给客户端；DTO 同时稳定外部契约。
- **Alternatives considered**: 直接返回领域对象实现更短，但安全性和兼容性不可接受。

## 7. OpenAPI、CORS 与静态管理页

- **Decision**: 复用已锁定的 springdoc，Swagger UI 入口统一为 `/swagger-ui`；核心阶段通过 MVC 配置开放所有来源。管理页使用同源纯 HTML/CSS/原生 JS，无构建链，五个导航分别读取 sessions、profiles、tools、memory、info。
- **Rationale**: 满足内网调试和单 fat JAR 部署，不增加 Node 工具链或独立前端服务。
- **Alternatives considered**: SPA 框架和独立部署超出核心阶段范围；拒绝。

## 8. Harness 与真实上下文位置

- **Decision**: `SessionApiControllerTest` 与 `GlobalExceptionHandlerTest` 位于 `oryxos-web`，使用 MVC 切片和 mock；`WebSmokeIT` 位于 `oryxos-boot`，使用 `OryxApplication` 启动聚合上下文并标记 `@Tag("integration")`。
- **Rationale**: Web 模块不能反向依赖聚合它的 boot 模块；只有 boot 测试能无循环依赖地验证全模块 Bean 和 JPA repository 扫描。
- **Alternatives considered**: 在 web 模块依赖 boot 仅供测试会制造模块循环；拒绝。
