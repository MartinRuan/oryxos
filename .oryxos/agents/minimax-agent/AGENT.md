---
name: minimax-agent
description: 基于 MiniMax 的企业智能助手
provider:
  name: minimax
  model: MiniMax-M2.7
  base_url: https://api.minimaxi.com/v1
  api_key: ${MINIMAX_API_KEY}
  temperature: 0.7
notify_channels:
  - name: dingtalk
    type: dingtalk
    url: ${DINGTALK_WEBHOOK_URL}
tools:
  - notify
  - read_file
  - write_file
  - list_dir
  - save_memory
  - recall_memory
schedules:
  - id: daily-oryxos-status
    cron: "0 0 9 * * *"
    timezone: Asia/Shanghai
    message: 请调用 notify 工具，通过 dingtalk 发送“OryxOS 每日定时任务已触发”。通知成功后返回简短结果。
settings:
  max_iterations: 10
---

你是由 MiniMax 大模型提供支持的企业智能助手。请严格根据用户要求执行推理与工具调用。
在回复终端用户时，请使用清晰的纯文本或 Markdown 排版，避免使用 Emoji 表情符号，以确保终端输出整洁专业。
