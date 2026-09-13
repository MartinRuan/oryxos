# 第 29 节验收报告：一个目录定义一个 Agent

- 验收日期：2026-09-13
- 分支：`029-lesson29-agent-directory`
- 结论：通过

## 交付行为

- `.oryxos/agents/<name>/AGENT.md` 的 frontmatter 派生现有 `Profile`，正文作为 Agent 主指令；目录名必须与 `name` 一致。
- 启动扫描按目录名稳定排序，单个坏目录只记录错误并隔离，不污染注册中心或阻断其他 Agent。
- Agent 私有 `skills/`、`scripts/`、`REFERENCE.md` 只识别路径，不预载正文；每轮上下文重新读取 AGENT.md 正文，支持修改后即时生效。
- `ProfileRegistry` 支持线程安全的 `register/remove/exists`，启动加载与运行时注册复用同一套必填字段、Provider 与 Tool 校验。
- `AgentScheduler.registerProfile` 支持单 Agent 注册；重复注册会取消并替换旧句柄，不产生双任务。
- 解释器仅能通过 argv 执行当前 Agent 的 `scripts/` 内真实文件；目录越界、符号链接越界和 `-c` 形式均被拒绝。
- 保留旧 `.oryxos/profiles` 与 `profiles` 来源的兼容加载，不增加新的 Profile 字段。

## 课件示例

以下四个文件存在并由仓库级回归测试直接解析：

- `.oryxos/agents/daily-reconcile/AGENT.md`
- `.oryxos/agents/daily-reconcile/REFERENCE.md`
- `.oryxos/agents/daily-reconcile/skills/report-format.md`
- `.oryxos/agents/daily-reconcile/scripts/reconcile.py`

真实启动发现课件中的流式 YAML 值 `url: ${OPS_WEBHOOK_URL}` 在变量未设置时无法被 SnakeYAML 解析，因此课件与交付文件均将该占位符修正为 `url: "${OPS_WEBHOOK_URL}"`。这仍使用环境变量，不含凭证，并保证设置或未设置变量时 YAML 都合法。

## 自动化验收

### 第 29 节联合 harness

执行：

```bash
./mvnw -pl oryxos-core,oryxos-tool -am spotless:apply test \
  -Dtest=AgentLoaderTest,DeriveProfileTest,AgentScanRegisterTest,ProfileRegistryRuntimeTest,AgentSchedulerRegisterTest,ProgressiveDisclosureTest,ShellToolsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`BUILD SUCCESS`。Core 13 项、Tool 4 项，共 17 项，0 失败、0 错误、0 跳过；六个规定 harness 均存在、非空且无 `@Disabled`。

| Harness | 覆盖内容 |
|---|---|
| `AgentLoaderTest` | frontmatter/正文拆分、私有资源识别、缺字段、空正文、仓库 daily-reconcile 示例真实解析 |
| `DeriveProfileTest` | identity/provider/tools/notify/bootstrap/settings/schedules 映射与目录名一致性 |
| `AgentScanRegisterTest` | N 个直接子目录、稳定扫描、坏目录隔离、未知 Tool 告警、注册与调度 |
| `ProfileRegistryRuntimeTest` | register/remove/exists、虚拟线程并发可见性、启动/运行时异常一致 |
| `AgentSchedulerRegisterTest` | cron/时区透传、Future 句柄、重复注册替换 |
| `ProgressiveDisclosureTest` | 只注入正文、不预载私有内容、正文热更新 |
| `ShellToolsTest` | 当前 Agent 脚本真实执行、输出、越界与 `-c` 拦截 |

### 全项目门禁

执行：`./mvnw clean verify`

最终结果：`BUILD SUCCESS`，12/12 Reactor 模块成功，总耗时 46.809 秒。Surefire 报告共 165 项测试，0 失败、0 错误、0 跳过。Spotless、Checkstyle、P3C/PMD、SpotBugs/FindSecBugs 与 Spring Boot 上下文启动全部通过。

## 真实启动只读验收

使用 `.oryxos/.env` 注入进程环境，在临时端口 `18080` 启动 fat JAR，仅调用只读接口，没有手动触发 schedule：

- `GET /api/v1/profiles` 返回 `daily-reconcile`，Provider 为 `minimax`，模型为 `MiniMax-M2.7`，工具为 `shell/read_file/notify/save_memory`。
- `GET /api/v2/schedules` 返回 `reconcile-morning`，cron 为 `0 0 9 * * *`，时区为 `Asia/Shanghai`，`enabled=true`，`runCount=0`。
- 启动日志确认 Agent 注册成功、定时句柄注册成功；随后已正常关闭临时服务。
- `daily-reconcile` 与现有 `minimax-agent` 共用显式 `minimax` Provider 映射。
- 从 `.oryxos/.env` 注入 `MINIMAX_API_KEY` 后，对 `daily-reconcile` 发起最小真实调用；MiniMax 返回 `MINIMAX_OK`，审计记录为 `provider=minimax`、`model=MiniMax-M2.7`、`success=true`、`totalTokens=633`，全程未调用工具或发送通知。

## H4 全局不变量

1. I/O 继续经过工具与沙箱；Shell 在启动进程前先执行命令白名单，并对脚本使用真实路径边界校验。
2. Agent 调用仍进入既有 `AgentService`/自实现 ReAct Loop；`llm_calls` 与 `tool_invocations` 审计路径未被绕过。
3. 新增示例只含环境变量占位符，没有硬编码 API key、数据库凭证或 webhook。
4. 未新增或修改 session ID 拼接规则。
5. 生产代码未引入 Reactor、CompletableFuture 或自建线程池，保持同步阻塞模型。
6. 未引入 Spring AI 自动 Tool 执行或 Agent 抽象，自实现 ReAct 调度边界保持不变。

## 剩余人工项

要验证一次完整业务执行，还需准备真实的昨日交易库与清算库导出，设置 `RECON_ORDERS_CSV`、`RECON_SETTLE_CSV`、可用 Provider 凭证和 `OPS_WEBHOOK_URL`。届时应确认定时触发创建 scheduler 会话、脚本输出正确、仅在差异时读取私有规范/参考、通知成功，并核对 `llm_calls` 与 `tool_invocations` 审计记录。本次验收没有发送 webhook。
