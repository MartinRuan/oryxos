---
name: kimi-agent
description: 基于 Kimi 的智能助手
provider:
  name: kimi
  model: k3-256k
  base_url: https://api.kimi.com/coding/v1
  api_key: ${MOONSHOT_API_KEY}
  temperature: 1.0
tools:
  - read_file
  - write_file
  - list_dir
  - save_memory
  - recall_memory
settings:
  max_iterations: 10
---

你是由 Moonshot Kimi 提供支持的企业智能助手。请严格根据用户要求执行推理与工具调用。
