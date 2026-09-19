# 第 30 节验收报告：Web Service 动态管理 Agent

- **验收日期**：2026-09-13
- **分支**：`030-lesson30-dynamic-agent-management`
- **结论**：通过自动化 harness、全量 Maven 门禁和隔离真实 MiniMax 冒烟；剩余浏览器视觉交互与真实 cron/webhook 见文末人工项。

## 1. 交付物核对

| 课件交付物 | 落地证据 |
|---|---|
| Agent 生命周期编排 | `AgentLifecycleService` 完成 create/register/list/get/update/delete/generate；API 创建和更新复用与 Watcher 相同的 `register(agentDir)`；失败恢复文件和运行时。 |
| Agent 工作区存储 | `AgentStore` 完成安全名称、原子写入、恢复、唯一归档、agents/archive 树和 UTF-8 文件读取；规范路径与真实路径双重拦截越界和符号链接逃逸。 |
| 实时目录监听 | `WorkspaceWatcher` 启动同步全量扫描，随后监听 agents 根目录与 Agent 子目录；坏目录单次失败会记录日志并继续处理后续事件；应用关闭时释放 WatchService。 |
| 定时注销 | `AgentScheduler.unregisterProfile(Profile)` 取消该 Agent 的全部运行句柄并退役当前任务，保留任务锁和历史执行记录。 |
| 一句话生成 | `AgentGenerationProperties` 默认显式使用 `minimax` / `MiniMax-M2.7`；生成通过既有 `ProviderService` 与 `SessionManager`，校验在内存中完成，不写目录、不注册、不执行 Tool。兼容 MiniMax `<think>` 前言、多个分隔线和 Markdown 围栏。 |
| REST API | `AgentApiController` 提供 generate/create/list/detail/update/delete 并保留 invoke；`WorkspaceApiController` 提供 tree/file；既有 `MemoryApiController` 通过可选 `agent` 参数查询关联记忆并保持无参数兼容。统一使用 `ApiResponse` 和业务错误码 40001/40002/40003。 |
| 管理平台 | `/admin/` 增加实时概览看板，展示 Agent、内置工具、活跃会话、Provider、运行状态、核心能力和技术栈；Agent 以列表展示，顶部可新建，操作栏支持详情、编辑、删除确认；详情页顶部可返回列表，并以基本信息、文件、会话、记忆四个 Tab 展示，其中文件限定到该 Agent 工作区，会话按 Agent 精确关联，记忆仅展示该 Agent 专属条目与历史共享条目；同时支持生成预览、CRUD、工作区目录树和只读文件预览，并展示服务端错误消息；左侧 OS运行时仅按 Provider、Tool、Sandbox白名单展示可折叠子菜单，右侧根据所选子菜单仅显示对应内容，一级入口默认显示 Provider。 |
| daily-reconcile Shell 修复 | 根据 `tool_invocations.input_json` 定位 MiniMax 传入组合 `command`、未声明 `cmd` 与白名单外 `find`；Agent 正文改为精确的 `python3` + `args` Tool 参数，并用真实解析器和隔离 CSV 验证。通知配置同步对齐现有 `minimax-agent` 的钉钉渠道；脚本返回 `error` 时明确停止，避免误报成功。 |
| Agent 记忆兼容 | `MemoryTools` 从 `ProfileContext` 取得当前 Agent，新条目在单个 `MEMORY.md` 中写入 `[agent:<name>]`；`MemoryService` 对 Prompt、检索与 Web 查询统一过滤为“当前 Agent + 历史共享”，返回时隐藏内部标记；既有全量读写和历史无标记内容保持兼容。 |
| 配置与目录 | `application.yaml` 增加 `oryxos.agent-generation.provider/model`；`.oryxos/archive/.gitkeep` 保留归档目录。未新增依赖、数据表或明文凭证。 |
| SDD 产物 | `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`、`tasks.md` 与需求 checklist 齐全。 |

`docs/TechnicalSolution.md` 已补充单文件 Agent 记忆标记、按 Profile 注入及管理台信息架构；用户更新后的第 30 节课件作为实现基线保留。第 29 节课件中的既有用户改动未被覆盖。

## 2. Harness 与关键回归

| 测试类 | 数量 | 验收点 |
|---|---:|---|
| `AgentLifecycleServiceTest` | 9 | 创建即注册；API/Watcher 共用 `register(Path)`；重名零写入；注册失败完整回滚；删除先注销再移索引和归档；更新先注销并在失败时恢复；安全视图脱敏；同名同毫秒归档仍唯一；并发重名创建仅一个成功；恢复阶段二次失败作为 suppressed exception 保留。 |
| `WorkspaceWatcherTest` | 2 | 启动初扫及手工增改删；5 秒窗口内同步；坏目录不拖垮后续事件；可关闭。 |
| `WorkspaceApiControllerTest` | 3 | agents/archive 稳定树、正常文件、缺失 404、目录/绝对路径/`../../etc/passwd` 400、符号链接越界 400 且不泄漏内容。 |
| `AgentApiControllerTest` | 5 | generate/CRUD 薄转发与统一信封、非法请求 400、既有 invoke/404/503/504 回归。 |
| `GenerateTest` | 4 | 合法草稿只生成不落盘；MiniMax 推理前言/围栏提取；非法草稿 40003；Provider 异常沿既有审计调用路径透传；显式 provider/model/session 身份与精确提示契约。 |
| `AgentSchedulerRegisterTest` | 2 | 重复注册替换句柄；注销取消句柄、退役任务，同时保留任务锁和执行历史。 |
| `LongTermMemoryTest` | 8 | 核心/归档既有行为；Agent 专属条目标记、历史共享可见、跨 Agent 隔离、返回隐藏内部标记。 |
| `MemoryServiceTest` | 5 | Session Profile 精确注入核心记忆；带 Agent 的 load/remember/recall 委托；无 Agent 全量行为兼容。 |
| `MemoryToolsTest` | 6 | `ProfileContext` 驱动 Agent 专属写入和检索；无上下文调用、Tool schema 与错误行为兼容。 |
| `MemoryApiControllerTest` | 1 | `GET /api/v1/memory?agent=` 精确查询与无参数全量查询兼容。 |
| `WebSmokeIT` | 3 | 真实 Spring 上下文、7 个 Controller/19 个 API 操作、OpenAPI、管理台 Agent/工作区入口与静态资源。 |

课件写出的两个关键测试方法已按原名落地：

- `注册失败_必须回滚已写的Agent目录_不留半个Agent`
- `删除必须先停定时_再动索引和目录`

## 3. 自动化门禁

最终执行：

```text
./mvnw clean verify
Tests: 192, Failures: 0, Errors: 0, Skipped: 0
Reactor: 12/12 modules SUCCESS
Spotless / Checkstyle / P3C-PMD / SpotBugs-FindSecBugs: PASS
BUILD SUCCESS
Total time: 47.313 s
```

daily-reconcile 聚焦回归：

```text
./mvnw -pl oryxos-core,oryxos-tool -am \
  -Dtest=AgentLoaderTest,ShellToolsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

隔离 CSV 直接执行 `python3 scripts/reconcile.py` 返回 2 条订单、2 条清算、两侧金额 125.5、`diffs=[]`。没有调用真实 Provider 或通知渠道。

全量测试包含前序各节回归。额外执行集成标签测试：

```text
./mvnw -pl oryxos-boot -am \
  -Dtest=WebSmokeIT,MemoryApiControllerTest,LongTermMemoryTest,MemoryServiceTest,MemoryToolsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

完整门禁 192 个测试与聚焦回归 23 个测试均为 0 failure/error/skip；聚焦回归中的 20 个 Memory/Web 单测已包含在完整门禁内。

## 4. 隔离真实 MiniMax 冒烟

使用仓库 `.oryxos/.env` 中的 `MINIMAX_API_KEY` 启动最终 fat JAR，工作目录和 SQLite 均位于 `/tmp/oryxos-lesson30-smoke`，未操作项目运行数据，也未输出密钥。

- 健康检查：HTTP 200，业务码 0。
- 最终 generate：首次 HTTP 200、业务码 0；返回 2457 字符且以合法 frontmatter 开始；生成前后活动 Agent 目录增量为 0；MiniMax `llm_calls` 审计增量为 1。
- 非法模型输出路径：真实测试中曾返回 40003，未落盘，调用仍写入审计；加强 OryxOS 字段形状提示后真实生成通过。
- create/detail/workspace file：均 HTTP 200；Provider 为 `minimax`，模型为 `MiniMax-M2.7`。
- create 后立即 invoke：HTTP 200、回复非空且包含约定的 `LESSON30_FINAL_OK`；生成了一条 invoke 会话并写入成功的 MiniMax 调用审计。
- `file?path=../../etc/passwd`：HTTP 400，没有返回工作区外内容。
- delete：HTTP 200；随后详情 HTTP 404；归档目录数量增加 1。
- 手工创建/移走 Agent 目录：上线和注销均在 5 秒内被 Watcher 识别，无需重启。
- 整个冒烟未配置 Tool、schedule 或通知渠道，因此没有触发外发通知。

## 5. H4 全局不变量

1. **涉外 IO 沙箱**：既有 Agent Tool 的文件、Shell、HTTP、Notify 入口仍先执行 `Sandbox.enforce`。本节管理面文件读取不经 Agent Tool，统一由 `AgentStore` 限制到 `.oryxos/agents` 与 `.oryxos/archive`，并校验 normalize/toRealPath，越界和外链均有测试。
2. **审计连续性**：generate/invoke 复用 `ProviderService`，真实成功、无效输出和 Provider 重试均进入 `llm_calls`；工具执行路径未改，仍由 `ToolExecutor` 同步记录 `tool_invocations`。`save_memory` 只增加当前 Profile 标记，不绕开 ToolExecutor。
3. **无明文凭证**：源码和配置扫描未发现硬编码 key；配置仍使用环境变量占位。`AgentView` 不暴露 Provider key/通知配置值，并对返回的 AGENT.md 敏感行脱敏；`.oryxos/.env` 未被 Git 跟踪。
4. **会话身份统一**：generate 使用 `SessionManager.getOrCreate("agent-generation", "designer", "agent-generator")`；invoke 和 scheduler 继续由 `SessionManager` 创建身份，本节代码不自行拼接 session id。
5. **同步阻塞模型**：请求链路未引入 Reactor、WebFlux、`CompletableFuture` 或自建线程池。仅按课件新增一个有明确 close 生命周期的 WatchService 守护线程。
6. **无 Spring AI 自动工具执行**：generate 只调用 `ProviderService.chat`，没有注册 Tool 或调用 `ChatClient.prompt()` 自动工具执行；ReAct/ToolExecutor 控制权未改变。

## 6. 剩余人工项

自动 harness 已判卷通过。以下项目依赖人工视觉或会产生真实业务外发，本次没有代替用户执行：

- 在浏览器打开 `/admin/`，完整查看生成预览、编辑表单、目录展开、错误提示和删除确认的视觉与键盘交互。
- 创建一个每分钟 cron 的临时 Agent，观察到点自动执行，并让真实 webhook 收到一次通知；完成后立即停用并归档。
- 在管理台展开 archive，人工确认归档目录和文件内容的展示效果。
