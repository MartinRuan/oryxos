# Quickstart: 验证目录型 Agent

## 前置条件

- JDK 21，项目 Maven Wrapper 可执行。
- 当前分支为 `029-lesson29-agent-directory`。
- 测试使用临时目录和模拟 Provider，不需要真实 API key 或 webhook。

## 1. 运行第 29 节 harness

```bash
./mvnw -pl oryxos-core,oryxos-tool -am test \
  -Dtest=AgentLoaderTest,DeriveProfileTest,AgentScanRegisterTest,ProfileRegistryRuntimeTest,AgentSchedulerRegisterTest,ProgressiveDisclosureTest,ShellToolsTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

预期：六个课件 harness 类全部执行且通过；ShellTools 的 Agent 脚本目录边界测试通过。

## 2. 验证示例目录

确认以下文件存在：

```text
.oryxos/agents/daily-reconcile/AGENT.md
.oryxos/agents/daily-reconcile/REFERENCE.md
.oryxos/agents/daily-reconcile/skills/report-format.md
.oryxos/agents/daily-reconcile/scripts/reconcile.py
```

启动应用后，`daily-reconcile` 应出现在 Profile 列表，且其 schedule 出现在定时任务列表。测试环境不实际连接交易库、清算库或发送 webhook。

## 3. 验证渐进式披露

通过 `ProgressiveDisclosureTest` 确认：

- prompt 包含 AGENT.md 正文；
- prompt 不包含 frontmatter、报告规范、参考资料或脚本源码；
- 修改正文后再次加载能读到新内容；
- `read_file` 和 `shell` 访问仍经过既有工具与沙箱。

## 4. 运行完整门禁

```bash
./mvnw clean verify
```

预期：第 16～28 节回归测试、本节测试、格式与静态质量门禁全部通过。

## 人工验收

配置真实 Provider、数据库与 `OPS_WEBHOOK_URL` 后：启动服务，确认定时任务到点创建 scheduler 会话、执行脚本、仅在有差异时读取报告格式/参考并发送通知，同时核对 `llm_calls` 与 `tool_invocations` 审计记录。真实外发测试需由环境持有人明确执行。
