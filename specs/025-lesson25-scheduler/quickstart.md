# Quickstart: Lesson 25 AgentScheduler

## 1. 在 `AGENT.md` 中声明定时任务

在 `.oryxos/agents/<agent-name>/AGENT.md` 的 frontmatter 中添加 `schedules`：

```yaml
---
name: weather-agent
description: 每日天气播报助手
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
schedules:
  - id: morning-report
    cron: "0 0 9 * * ?"
    timezone: "Asia/Shanghai"
    message: "获取最新天气并发送早间推送"
---

你是一个天气播报助手。当收到播报消息时，调用 http_get 查询天气并用 notify 发送。
```

## 2. 系统启动即调度

启动应用后，`AgentScheduler` 将自动注册所有 Agent 的定时规则：
- 到点自动触发 `AgentService.process`
- 会话使用固定身份 `("scheduler", "scheduler", "weather-agent")`
- 前序任务未完成时跳过本次触发
- 失败安全记日志并写入审计表 `llm_calls` 和 `tool_invocations`
