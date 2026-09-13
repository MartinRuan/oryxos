# 第 28 节自动调度与重启恢复全流程验收报告

- 验收日期：2026-09-12
- 分支：`028-lesson28-scheduler-recovery-flow`
- 结论：自动化 harness、全量质量门禁、真实进程重启、真实 MiniMax、钉钉通知，以及定时任务持久化管理均通过

## 对账范围

本节按串联课模式执行，没有创建新的 Spec Kit feature。验收覆盖钟推链路、连续调度的
Session 复用、Notify 失败恢复、跨 Spring 上下文重启恢复、多 Agent 隔离，以及超时、审计、
日志和 MCP 启动容错。

课件将一次“查天气 → 推送 → 最终答复”记为两次 LLM 调用，但当前自实现 ReAct Loop 每次
模型响应只执行一组工具调用。方案 A 保留这项既有语义，因此一次完整钟推链路精确产生三次
LLM 调用和两次工具调用：请求 `http_get`、请求 `notify`、生成最终答复。该差异已由用户确认，
测试按三次 LLM、两次 Tool 判卷。

## 对账中修复的接缝

1. 为 Profile 加载与 Schedule 注册的两个 `ApplicationRunner` 增加确定顺序，保证启动时先加载
   Profile，再重建定时任务。
2. `AgentServiceImpl` 在调用主线写入 MDC `sessionId`，并在成功或异常后恢复原值；控制台、文件和
   JSON 日志均输出该字段。
3. 去掉 `ProviderRegistry` 的组件扫描入口，使 `ProviderAutoConfiguration` 始终创建并填充显式
   Provider 映射，避免空 Registry 抢先注册。
4. 为 OpenAI 兼容协议与 DashScope 的同步 HTTP 客户端接入既有
   `oryxos.default-timeout-seconds`；旧的单参数工厂方法保留并使用 120 秒默认值。
5. 为默认 Webhook 客户端设置 10 秒连接和读取超时；既有 `HttpTools` 与 `ShellTools` 也各有
   10 秒执行超时，Web Agent 调用保留 60 秒整体上限。
6. 为第 26 节以前创建的 SQLite 文件补充 `llm_calls.success` 与 `error_message` 兼容迁移，重启
   测试从旧表结构启动并验证升级成功。
7. 启动冒烟测试改用 `/tmp` 数据库，门禁运行不再修改仓库内跟踪的 `.oryxos/oryxos.db`。
8. 管理页的 Session 卡片支持按需读取并展开完整消息；Profiles 页面更名为 Agents，安全展示
   Provider、模型和工具，不返回任务消息、API Key 或 Webhook URL。
9. 新增独立 Schedules 页面与 v2 管理 API，可列出任务、启动、停止、立即执行并查询执行历史；
   列表和更新响应均不返回任务消息。
10. 新增 scheduled_tasks 与 task_executions 持久化表。AGENT.md 仍是任务定义源，SQLite 保存稳定
   scheduleId、启停状态、下次/上次运行时间、结果、次数和执行历史；配置协调不会覆盖人工停用状态。

## SchedulerFlowIT

测试类：`oryxos-boot/src/test/java/com/oryxos/boot/SchedulerFlowIT.java`

该测试标记为 `@Tag("integration")`，使用可编排离线 `ChatModel` 和进程内 HTTP 服务，执行真实
Spring Boot Bean、WhitelistSandbox、SQLite Repository 和 ReAct Loop。

| 用例 | 自动判卷内容 | 结果 |
| --- | --- | --- |
| `定时任务连续触发复用会话并精确记录三次模型两次工具` | 连续两次触发仍只有一个 Session；12 条消息、6 条 LLM 审计、4 条 Tool 审计；天气与通知端点各命中两次 | 通过 |
| `通知被沙箱拒绝会写失败审计且下一次调度仍可成功` | 白名单外域名被拒绝，`notify` 写 `success=false` 和可读错误；下一次触发完成并成功通知 | 通过 |
| `多Agent的工具会话和调度锁相互隔离` | A 仅见 `read_file`、B 仅见 `http_get`；Session ID、LLM 审计和调度锁相互隔离；A 失败后 B 继续成功 | 通过 |

成功链路的消息顺序为：

```text
USER → ASSISTANT(http_get) → TOOL → ASSISTANT(notify) → TOOL → ASSISTANT
```

## RestartRecoveryIT

测试类：`oryxos-boot/src/test/java/com/oryxos/boot/RestartRecoveryIT.java`

测试连续创建两套独立 Spring 上下文，并复用同一个临时 SQLite、`MEMORY.md` 与 Agent 目录，等价
验证进程内对象全部销毁后的恢复行为：

| 恢复项 | 自动判卷内容 | 结果 |
| --- | --- | --- |
| Profile 与 Schedule | 第二套上下文重新读取 `AGENT.md`，Profile 配置一致，Schedule 恰好重新注册一次 | 通过 |
| Session | 重启前两条消息可恢复，重启后沿用同一 Session ID 追加到四条 | 通过 |
| Memory | Core Memory 写入文件，第二套上下文可读取原内容 | 通过 |
| 双审计 | 重启前后 `llm_calls` 从 1 增至 2，`tool_invocations` 从 1 增至 2 | 通过 |
| 旧库升级 | 从缺少 `success/error_message` 的旧 `llm_calls` 表启动，迁移后 Provider 审计正常写入 | 通过 |

## 定向集成测试

```text
./mvnw -pl oryxos-boot -am \
  -Dtest=SchedulerFlowIT,RestartRecoveryIT,AgentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

SchedulerFlowIT: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
RestartRecoveryIT: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
AgentServiceTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 8.075 s
```

两个课件指定的集成测试文件共 597 行，均非空且带 `@Tag("integration")`。它们按 `*IT` 命名，
默认 Surefire 门禁不执行，因此发版前需保留上述定向命令。

## 全量门禁

```text
./mvnw clean verify

OryxOS ............................................. SUCCESS
OryxOS Core ........................................ SUCCESS
OryxOS Storage ..................................... SUCCESS
OryxOS Provider .................................... SUCCESS
OryxOS Memory ...................................... SUCCESS
OryxOS Knowledge ................................... SUCCESS
OryxOS Tool ........................................ SUCCESS
OryxOS Channel CLI ................................. SUCCESS
OryxOS Channel Feishu .............................. SUCCESS
OryxOS Web ......................................... SUCCESS
OryxOS CLI ......................................... SUCCESS
OryxOS Boot ........................................ SUCCESS

BUILD SUCCESS
Total time: 46.551 s
```

默认门禁共执行 45 个测试套件、151 个测试，0 failures、0 errors、0 skipped。连同两个定向
集成测试类，当前 Surefire 报告共 47 个套件、155 个测试，全部通过。门禁包含 Checkstyle、
Spotless、PMD/P3C、SpotBugs/Find Security Bugs。

Provider 超时另由 `ProviderAutoConfigurationTest` 使用 1 秒配置和延迟 5 秒的本地 HTTP 端点
判卷，实际得到 `SocketTimeoutException: Read timed out`，证明配置已传入底层同步客户端。

## 前序能力与本节交付物核对

| 能力或交付物 | 证据 | 结果 |
| --- | --- | --- |
| `SchedulerFlowIT` | 三个钟推、多 Agent 和失败恢复用例 | 存在且通过 |
| `RestartRecoveryIT` | 两套独立上下文复用外置状态 | 存在且通过 |
| Provider | 显式 Registry 恢复；LLM 超时配置生效；成败审计回归通过 | 通过 |
| ReAct | 自实现循环不变；方案 A 精确核对每次三次 LLM、两次 Tool | 通过 |
| Session | Scheduler 三元组集中生成；连续触发与跨重启均复用 | 通过 |
| Notify 与 Sandbox | 本地 Webhook 真接收；白名单拒绝写失败审计；后续触发成功 | 通过 |
| Memory | 文件后端在独立上下文间恢复 | 通过 |
| Storage | Session、LLM、Tool 审计连续；旧 SQLite 结构可升级 | 通过 |
| MCP | 单个 Server 连接异常记 WARN 并跳过，既有失联测试回归通过 | 通过 |

## H4 全局不变量

1. **涉外 IO 先过沙箱**：生产代码扫描确认文件读写和列目录、Shell、HTTP、Notify 执行前均调用
   `Sandbox.enforce`；Scheduler 失败用例验证被拒请求没有到达通知端点。
2. **双审计 Day One 写入**：Provider 的成功、空响应和异常路径均写 `llm_calls`；
   `ToolExecutorImpl` 的成功、失败结果、未知工具和异常路径均写 `tool_invocations`。本节集成测试
   对成功与失败记录分别断言。
3. **无生产明文凭证**：生产源码凭证特征扫描命中数为 0；测试只使用明确的 `test-key` 假值。
4. **会话 ID 集中生成**：三元组拼接只存在于 `SessionManager.generateSessionId`，JPA 实现和
   Scheduler 均调用该方法。
5. **同步阻塞模型**：生产源码无 Reactor 类型、`CompletableFuture`、`Executors` 或
   `new Thread`；Provider 同步调用使用有超时的 `RestClient`，WebClient Builder 仅为锁定版
   Spring AI 构造器的必需参数。
6. **Spring AI 不自动执行 Tool**：无 `ChatClient` 自动工具路径；OpenAI 和 DashScope 模型的
   `isToolCall` 均返回 `false`，工具只由 `ReActLoop + ToolExecutor` 执行。

## 真实进程恢复验收

使用无外部凭证的临时 `lesson28-live-mock` Profile 和隔离 SQLite 启动实际 fat JAR，避免测试
数据或请求离开本机：

1. 服务在 `18082` 启动，`GET /api/v1/health` 返回 `UP`，Profile 自动加载成功。
2. 通过 REST 创建固定 Session 并发送消息，Session 保存 USER、ASSISTANT 两条消息；
   `llm_calls` 写入一条 `mock/mock-model` 成功审计，total tokens 为 30。
3. 先完成一次正常停止、重启与 Session 查询，外置数据完整恢复。
4. 在另一份隔离数据库上对 Java 进程执行 `kill -9`，随后重启同一 fat JAR；健康状态仍为 `UP`，
   同一 Session 的消息、时间字段和 LLM 审计均完整恢复。
5. 测试服务已停止，临时 Profile、SQLite 和写入项目运行库的测试行均已删除，工作区无测试残留。

## 真实外部环境验收

经用户明确授权，加载 `.oryxos/.env` 中的 `MINIMAX_API_KEY` 与
`DINGTALK_WEBHOOK_URL` 完成真实调用；报告不记录任何凭证值。

1. 使用 `minimax-agent` 发送固定文本，MiniMax 返回 `LESSON28_REAL_PROVIDER_TEST`；Session 有
   两条消息，`llm_calls` 有一条成功记录，total tokens 为 428。
2. 使用独立 Session 要求 MiniMax 调用 `notify`，钉钉 Webhook 成功接收“OryxOS 第28节通知链路
   测试”；模型最终返回 `LESSON28_NOTIFY_OK`。
3. Notify Session 的消息顺序为 USER → ASSISTANT(tool call) → TOOL → ASSISTANT；
   `llm_calls` 两条均成功，total tokens 合计 1044；`tool_invocations` 恰好一条 `notify`，
   `success=true` 且无错误信息。
4. 创建临时 Profile `lesson28-real-scheduler`，使用 `0 */2 * * * *` 在 20:54:00 由
   `AgentScheduler` 自动触发。调度 Session 的 channel/user 均为 `scheduler`，包含四条消息；
   两条 MiniMax 审计均成功，total tokens 合计 771；一条 `notify` 审计成功且无错误信息。
5. 自动触发完成后在下一触发点前停止服务，并删除临时 Profile 和隔离数据库，确保只产生一次
   自动外发。两条直接触发的真实验收 Session 与审计记录保留在项目运行库，便于后续复核。

## 定时任务持久化管理验收

1. 管理页新增独立 Schedules 导航和卡片，展示 Agent、Cron、时区、启停状态、下次/上次运行、
   最近结果与执行次数；提供 Start、Stop 和 Run now 控制。
2. 新增并实测五个 v2 端点：任务列表、执行历史、按 scheduleId 立即执行、启停更新，以及按
   Agent/任务 key 立即执行。API 响应不返回触发消息和任何凭证。
3. AgentScheduler 启动时将 AGENT.md 定义协调到 SQLite。首次创建使用稳定 UUID；重新加载配置
   保留人工启停状态，删除配置则退役任务并保留执行历史。
4. 使用实际 fat JAR 在 18083 启动，识别 minimax-agent/daily-oryxos-status，Cron 为
   0 0 9 * * *，时区 Asia/Shanghai。通过 PUT 停止任务后正常重启，任务 ID 保持
   68ced736-b694-4bcf-b565-b6208947c0c4，enabled 仍为 false，nextRunAt 为 null。
5. 该任务的 runCount 为 0，执行历史为空。本轮只验证启停和重启恢复，没有点击 Run now，
   也没有产生新的钉钉外发。
6. 管理页 /admin 正确重定向到 /admin/index.html；实际静态资源包含 Schedules 页面及三项控制。
   原只读文案已改为 Operations console 与 Runtime connected。

## 剩余人工项

自动化 harness 已判卷，以下项目需要真实外部环境或真实进程操作，留给人工验收：

1. 配置一个真实但不可达的 MCP Server，确认启动日志只记录 WARN，健康检查和本地工具仍可用。
2. 第 31 节 Demo 上场前逐项确认六项环境：Sandbox 白名单、通知渠道、新闻 MCP、记忆偏好、定时
   配置、跨重启数据。

本节未执行 commit、push 或 `package.sh`。
