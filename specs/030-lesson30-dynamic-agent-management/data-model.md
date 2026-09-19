# Data Model: 动态管理 Agent

本节不新增数据库表。Agent 定义以文件系统为主数据，运行时 Profile、调度句柄与既有 SQLite 审计/任务状态是派生或既有数据。

## 1. AgentDefinition

表示 `.oryxos/agents/<name>/` 中的完整 Agent 定义。

| Field | Type | Required | Rules |
|---|---|---:|---|
| name | string | yes | 与目录名完全一致；非空；不得含 `..`、`/`、`\\` 或绝对路径形式 |
| description | string | yes | 非空的人类可读用途说明 |
| instructions | string | yes | `AGENT.md` frontmatter 后的正文，非空 |
| identity | object | no | 复用现有 Profile identity 字段 |
| provider | object | yes | name 必填且必须在显式 Provider 映射中；model 为空时使用该 Provider 默认模型 |
| tools | string[] | no | 复用现有 Tool 注册名称；未知项沿用现有校验/告警规则 |
| mcpServers | string[] | no | 复用现有 MCP server 名称 |
| notifyChannels | object[] | no | 仅保留配置引用/环境变量占位符，不向响应展开密钥 |
| schedules | ScheduleDefinition[] | no | id 在 Agent 内唯一；cron 与 zone 必须可解析；message 非空 |
| bootstrap | string[] | no | 复用既有 Bootstrap 文件约定 |
| settings | object | no | 复用现有迭代数和历史窗口约定 |
| sourceDirectory | relative path | yes | 固定为 `agents/<name>/`，外部响应不返回绝对路径 |
| agentMarkdown | string | internal | 原始定义文本；对外展示前不得包含展开后的环境变量密钥 |

### Relationships

- 一个 AgentDefinition 派生一个 AgentRuntimeRegistration。
- 一个 AgentDefinition 可以包含零到多个 ScheduleDefinition。
- 删除后整个 AgentDefinition 转为 ArchivedAgent，不再有活动运行时注册。

## 2. ScheduleDefinition

| Field | Type | Required | Rules |
|---|---|---:|---|
| id | string | yes | Agent 内唯一，复用既有 schedule key |
| cron | string | yes | 必须由既有 cron 解析规则接受 |
| zone | string | yes | 必须是合法时区；未给定时沿用现有默认 |
| message | string | yes | 定时触发时送入 Agent 的用户消息 |

更新 schedules 时，以整个列表为新集合；运行时必须先注销旧集合，再注册新集合。

## 3. AgentRuntimeRegistration

表示由 AgentDefinition 派生的进程内状态。

| Field | Type | Required | Rules |
|---|---|---:|---|
| profileName | string | yes | 等于 AgentDefinition.name |
| profile | Profile | yes | 由现有 AgentLoader 派生，不单独持久化 |
| scheduleHandles | map | no | 由 AgentScheduler 维护，键与既有持久化任务状态对应 |
| sourceRevision | file metadata | internal | 仅用于监听事件去重/诊断，不成为对外版本概念 |

## 4. GeneratedDraft

表示一句话生成的待确认草稿。

| Field | Type | Required | Rules |
|---|---|---:|---|
| sentence | string | yes | 非空，并受请求长度限制 |
| agentMarkdown | string | yes | 必须通过与正式 Agent 相同的文档拆分、必填字段和 Provider 校验 |
| provider | string | yes | 来自 `oryxos.agent-generation.provider`，默认 `minimax` |
| model | string | yes | 来自 `oryxos.agent-generation.model`，默认 `MiniMax-M2.7` |
| persisted | boolean | yes | 生成流程恒为 false |
| registered | boolean | yes | 生成流程恒为 false |

GeneratedDraft 不写 Agent 目录，不进入 ProfileRegistry，不注册 schedule。它只有在用户将内容提交给正式创建操作后才成为 AgentDefinition。

## 5. ArchivedAgent

| Field | Type | Required | Rules |
|---|---|---:|---|
| originalName | string | yes | 原 Agent 名称 |
| archiveName | string | yes | `<name>-<UTC时间戳>`；冲突时追加唯一后缀 |
| archivePath | relative path | yes | 必须位于 `.oryxos/archive/` |
| archivedAt | instant | yes | 归档动作时间 |
| files | directory tree | yes | 原 Agent 目录的完整内容 |

ArchivedAgent 不拥有活动 Profile 或 schedule 句柄，但既有 session、llm_calls、tool_invocations 与 schedule 执行历史保持不变。

## 6. WorkspaceNode

| Field | Type | Required | Rules |
|---|---|---:|---|
| name | string | yes | 当前节点文件名 |
| path | string | yes | 相对 `.oryxos/` 的规范化路径，不返回绝对路径 |
| type | enum | yes | `DIRECTORY` 或 `FILE` |
| children | WorkspaceNode[] | directory only | 目录按名称稳定排序；文件为空列表 |
| readable | boolean | yes | 仅普通 UTF-8 文本文件为 true |

工作区树只暴露 `agents/` 与 `archive/` 两棵子树。文件读取必须对规范路径和真实路径执行两次根边界检查。

## 7. AgentMemoryEntry

继续存放于 `.oryxos/memory/MEMORY.md`，不新增数据库表或 Agent 独立文件。

| Field | Type | Required | Rules |
|---|---|---:|---|
| date | local date | yes | 沿用现有 `[yyyy-MM-dd]` 日期前缀 |
| profileName | string | new entries yes | 以 `[agent:<name>]` 写入；历史无标识条目视为共享 |
| scope | enum | yes | 由所在的核心/归档分区表达 |
| content | string | yes | 沿用现有 Agent 自主写入文本 |

读取指定 Agent 时包含 `profileName` 精确匹配的条目及历史共享条目，排除其他 Agent 条目；返回给 Prompt、Tool 或 Web 页面时移除内部 Agent 标记。无 Agent 上下文的既有调用继续读写全量共享视图。

## 8. Lifecycle State Transitions

```text
ABSENT
  └─ create/write → WRITTEN
       └─ derive + register → ACTIVE
            ├─ update/write candidate → UPDATING
            │    ├─ register succeeds → ACTIVE(new definition)
            │    └─ register fails → ACTIVE(original definition restored)
            └─ delete: unregister schedules → remove registry → archive → ARCHIVED

GENERATING
  ├─ valid draft → DRAFT_ONLY (no file/runtime state)
  └─ invalid/provider failure → FAILED (audit retained, no file/runtime state)
```

### Invariants

1. ACTIVE 必须存在对应 Agent 目录和可派生的 AGENT.md。
2. 同一 name 同时最多一个 ACTIVE 运行时注册。
3. ACTIVE 的每个 schedule key 同时最多一个调度句柄。
4. ARCHIVED 不得保留活动 Profile 或调度句柄。
5. DRAFT_ONLY 不得产生任何 Agent 目录、Profile 或调度句柄。
6. 失败回滚结束后只允许回到操作前稳定状态，不允许停留在 WRITTEN 或 UPDATING。
