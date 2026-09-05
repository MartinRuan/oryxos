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
settings:
  max_iterations: 10
---

你是由 MiniMax 大模型提供支持的企业智能助手。请严格根据用户要求执行推理与工具调用。
