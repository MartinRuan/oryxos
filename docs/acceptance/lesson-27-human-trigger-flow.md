# 第 27 节人触发全流程验收报告

- 验收日期：2026-09-12
- 分支：`027-lesson27-human-trigger-flow`
- 结论：自动化验收与真实环境验收均通过

## 对账范围

本节按串联课模式执行，没有创建新的 Spec Kit feature。验收覆盖 Provider、ReAct Loop、CLI
共享入口、九个内置 Tool、Memory、Sandbox、Scheduler、Web API，以及 `sessions`、
`llm_calls`、`tool_invocations` 三张 SQLite 表。

对账中发现并修复了四个真实装配接缝：

1. 组合型文件、HTTP、Memory 工具只进入了 `ToolRegistry`，没有作为独立
   `OryxTool` Bean 进入 `ToolExecutor`，导致 ReAct Loop 无法执行这些工具。
2. `ToolRegistry` 同时参与组件扫描和条件自动配置，空构造 Bean 使负责装载工具的自动配置
   被跳过，`GET /api/v1/tools` 返回空列表。
3. `ProfileLoader` 被 Spring 实例化时选中了无参构造器，造成 Agent 文件虽显示加载成功却没有
   注册。
4. Maven 启动进程的工作目录位于 `oryxos-boot`，无法看到项目根目录的 `.oryxos`。

修复后，`ToolExecutor` 与 `ToolRegistry` 均纳管以下九个既有工具：
`read_file`、`write_file`、`list_dir`、`shell`、`http_get`、`http_post`、
`save_memory`、`recall_memory`、`notify`。

## HumanTriggerFlowIT

测试类：`oryxos-boot/src/test/java/com/oryxos/boot/HumanTriggerFlowIT.java`

该测试标记为 `@Tag("integration")`。测试用可编排离线 `ChatModel` 和进程内 HTTP
天气服务隔离外部网络，其余部分全部使用真实 Spring Boot Bean 和 SQLite：
`SessionManager` → `AgentService` → `ReActLoop` → `ProviderService` →
`ToolExecutor` → `WhitelistSandbox` → JPA 审计。

| 用例 | 自动判卷内容 | 结果 |
| --- | --- | --- |
| CLI 人触发主链 | “今天北京天气怎么样，穿什么合适”；两次 LLM、一次 `http_get`、四条会话消息；Web 可查询同一会话 | 通过 |
| REST 人触发主链 | 创建会话、发送消息、查询详情；与 CLI 共用执行链并完成三表对账 | 通过 |
| 失败与恢复 | Provider 异常、Sandbox 拒绝、Tool 异常均写 `success=false` 和错误原因；后续会话继续成功 | 通过 |

成功会话的精确对账结果：

- `sessions`：一条会话，消息顺序为 USER → ASSISTANT(tool call) → TOOL → ASSISTANT。
- `llm_calls`：恰好两条，`session_id` 一致，均 `success=true`，三项 token 均大于 0。
- `tool_invocations`：恰好一条 `http_get`，`success=true`，结果含北京、25℃、晴。
- 本地天气服务命中恰好一次，证明同一 Tool 没有被 Spring AI 和 ReAct Loop 重复执行。
- `.oryxos/memory/MEMORY.md` 在 CLI、REST 天气问答前后内容一致。
- CLI 与 Web 会话 ID 均由 `SessionManager.generateSessionId` 生成。

定向执行：

```text
./mvnw -pl oryxos-boot -am -Dtest=HumanTriggerFlowIT \
  -Dsurefire.failIfNoSpecifiedTests=false test

Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 全量门禁

```text
./mvnw clean verify

Reactor Summary:
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
Total time: 44.782 s
```

Surefire 报告汇总：41 个默认测试套件，145 个测试，0 failures，0 errors。门禁包含
Checkstyle、Spotless、PMD/P3C、SpotBugs/Find Security Bugs。定向集成测试按 `*IT`
命名独立执行，不混入默认单测。

## 真实环境验收

使用项目根目录 `.oryxos/.env` 中的既有配置完成测试，报告只记录配置状态，不记录凭证值。
`MINIMAX_API_KEY` 与 `DINGTALK_WEBHOOK_URL` 均已设置；本次未向钉钉发送消息。

启动命令：

```text
./mvnw -pl oryxos-boot -am spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=18080
```

Maven 插件的工作目录已固定到多模块项目根目录。服务启动后成功加载 `ops-agent`、
`kimi-agent`、`minimax-agent`，`GET /api/v1/health` 返回 `UP`，
`GET /api/v1/tools` 返回九个工具及各自完整 Schema。

MiniMax 纯模型调用结果：

- Provider：`minimax`，模型：`MiniMax-M2.7`，返回内容包含“MiniMax 环境测试通过”。
- `sessions`：`web:lesson27-env-test:minimax-agent` 已落库并保持 `ACTIVE`。
- `llm_calls`：一条成功审计，prompt/completion/total token 为 393/25/418，耗时 1363 ms。
- `tool_invocations`：零条，符合该请求无需调用工具的预期。

MiniMax 真实 ReAct 工具调用结果：

- 使用只含 `ORYXOS_LESSON27_TOOL_CHAIN_OK` 的临时文件验证 `read_file`，测试后已删除文件。
- 会话消息严格为 USER → ASSISTANT(tool call) → TOOL → ASSISTANT，共四条。
- `llm_calls` 恰好两条且均成功；`tool_invocations` 恰好一条 `read_file`，
  `success=true`，耗时 2 ms。
- 最终回复包含测试标记，证明真实 MiniMax Function Calling、ReAct Loop、Sandbox、
  ToolExecutor、SQLite 双审计链路完整可用。

## 前序能力核对

| 能力 | 证据 | 结果 |
| --- | --- | --- |
| Provider | 显式 `ProviderRegistry` 映射；成功与失败均写 `llm_calls` | 通过 |
| ReAct | 自实现循环，最大轮次生效，每轮消息累积进 Session | 通过 |
| CLI | `CliChannel` 调用统一 `AgentService`；前序 CLI 测试回归通过 | 通过 |
| Notify | `NotifyTools` 经 Sandbox 后调用适配器；缺失渠道测试回归通过 | 通过 |
| Tool | 九个内置 Tool 使用统一 `OryxTool`，Registry 与 Executor 均可用 | 通过 |
| Memory | Core/Archival 实现和零缓存测试回归通过；本节天气流未修改 Memory | 通过 |
| Sandbox | 文件、Shell、HTTP 白名单测试回归通过；本节固化 HTTP 拒绝审计 | 通过 |
| Scheduler | Cron 注册、同任务不重入、异常释放锁测试回归通过 | 通过 |
| Web | Session REST 主链、统一 JSON 错误和 OpenAPI 测试回归通过 | 通过 |

## H4 全局不变量

1. **涉外 IO 先过沙箱**：文件读写/列目录、进程启动、HTTP 发送和 Notify 发送前均调用
   `Sandbox.enforce`；集成测试验证白名单拒绝不会发起网络请求。
2. **双审计 Day One 写入**：Provider 成功、空响应和异常分支均写 `llm_calls`；
   Tool 成功、失败结果、未知工具和异常分支均写 `tool_invocations`。
3. **无生产明文凭证**：生产源码与配置的凭证特征扫描无命中；仅单元测试中存在明确的
   `sk-*-test-key` 假值。
4. **会话 ID 集中生成**：会话三元组拼接只存在于
   `SessionManager.generateSessionId`，JPA 与内存实现均调用它。
5. **同步阻塞模型**：生产源码无 Reactor、`CompletableFuture`、`Executors`、
   `new Thread`；Web 和调度只使用 Spring 管理的执行器。
6. **Spring AI 不自动执行 Tool**：无 `ChatClient` 自动工具路径；
   OpenAI 与 DashScope 模型的 `isToolCall` 均覆写为 `false`，工具只由
   `ReActLoop + ToolExecutor` 执行。

## 剩余人工项

自动化 harness、真实 MiniMax REST 调用和真实 `read_file` 工具链已经判卷。发布前仍需完成：

1. 从交互式 CLI 执行一次真实 Provider 对话，确认 CLI 与已通过的 REST 链路语义一致。
2. 经用户明确授权后执行一次 `notify`，确认钉钉接收端收到消息。

本节未执行 push 或 `package.sh`。
