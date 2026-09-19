# REST Contract: Agent Management and Workspace

Base response envelope follows the existing contract:

```json
{"code": 0, "message": "Success", "data": {}, "timestamp": 1789276896829}
```

No endpoint returns resolved API keys, webhook secrets, absolute filesystem paths, stack traces, or internal exception details.

## 1. Generate Agent Draft

`POST /api/v1/agents/generate`

Request:

```json
{"sentence": "每天早上九点查北京天气，把穿搭建议发到团队群"}
```

Validation:

- `sentence` is required, non-blank, maximum 4096 characters.
- Provider/model come from `oryxos.agent-generation.provider/model`; defaults are `minimax` and `MiniMax-M2.7`.

Success `200`:

```json
{
  "code": 0,
  "message": "Success",
  "data": "---\nname: weather-daily\n...\n---\n你是每日天气助手……",
  "timestamp": 1789276896829
}
```

The operation validates the generated text with the same Agent document rules used by formal registration. It does not write a file, register a Profile, or schedule a task. Provider success and failure both enter `llm_calls`.

Errors:

- `400 / 40000`: blank or oversized sentence.
- `400 / 40003`: model output is not a valid Agent definition; message identifies the validation reason without exposing prompt or credentials.
- Existing Provider failure codes/statuses remain unchanged for timeout or unavailable Provider.

## 2. Create Agent

`POST /api/v1/agents`

The request supports exactly one input form.

### Raw AGENT.md form

```json
{
  "name": "weather-daily",
  "agentMarkdown": "---\nname: weather-daily\ndescription: 每日天气\nprovider:\n  name: minimax\n  model: MiniMax-M2.7\ntools: [http_get, notify]\nschedules:\n  - {id: weather-morning, cron: \"0 0 8 * * *\", zone: Asia/Shanghai, message: 查询天气并推送建议。}\n---\n你是每日天气助手。"
}
```

### Structured form

```json
{
  "name": "weather-daily",
  "description": "每日天气",
  "instructions": "你是每日天气助手。",
  "identity": {"agentName": "天气小欧", "prompt": "回答简洁准确。"},
  "provider": {"name": "minimax", "model": "MiniMax-M2.7", "temperature": 0.3},
  "tools": ["http_get", "notify"],
  "mcpServers": [],
  "notifyChannels": [],
  "schedules": [
    {"id": "weather-morning", "cron": "0 0 8 * * *", "zone": "Asia/Shanghai", "message": "查询天气并推送建议。"}
  ],
  "bootstrap": ["AGENTS.md", "SOUL.md", "USER.md"],
  "settings": {"maxIterations": 10, "maxHistoryTurns": 20}
}
```

Validation:

- `name` is required and must be a safe single directory name.
- Raw and structured forms are mutually exclusive.
- Raw frontmatter name must equal request name and directory name.
- Structured form requires description, instructions, and provider.name.
- Provider must exist in the explicit Provider registry; schedule cron/zone must parse.
- Secret fields in submitted Agent content must use environment placeholders, not literal credentials.

Success `200`: `ApiResponse<AgentView>` as defined below. The Agent is queryable and invocable before the response returns.

Errors:

- `400 / 40001`: Agent name already exists; no directory is written.
- `400 / 40002`: invalid name, Agent definition, provider reference, tool/schedule shape, or conflicting input forms.
- `500 / 50000`: write/register failure after rollback; no partial Agent remains.

## 3. List Agents

`GET /api/v1/agents`

Success `200`: `ApiResponse<AgentView[]>`, sorted by Agent name. No lazy filesystem registration is triggered by this read.

## 4. Get Agent

`GET /api/v1/agents/{name}`

Success `200`: `ApiResponse<AgentView>`.

Missing Agent: `404 / 40401`.

## 5. Update Agent

`PUT /api/v1/agents/{name}`

Request accepts either a complete `agentMarkdown` replacement or any subset of structured fields from Create except `name`. Empty updates are rejected. If raw content is supplied, its frontmatter name must equal the path name.

Example partial update:

```json
{
  "provider": {"name": "minimax", "model": "MiniMax-M2.7", "temperature": 0.2},
  "schedules": [
    {"id": "weather-morning", "cron": "0 30 8 * * *", "zone": "Asia/Shanghai", "message": "查询天气并推送建议。"}
  ]
}
```

Success `200`: updated `ApiResponse<AgentView>`.

Semantics:

1. Preserve the original definition until the candidate passes validation.
2. Replace AGENT.md atomically.
3. For schedules, unregister old handles before registering the new set.
4. If registration fails, restore the original file and original runtime registration.

Errors:

- `400 / 40002`: invalid update or attempted name change.
- `404 / 40401`: Agent does not exist.
- `500 / 50000`: update failed after restoration attempt; error is logged without response internals.

## 6. Delete Agent

`DELETE /api/v1/agents/{name}`

Success `200`:

```json
{"code": 0, "message": "Success", "data": null, "timestamp": 1789276896829}
```

The observable order is: unregister schedules, remove runtime Profile, move the entire Agent directory to a unique child of `.oryxos/archive/`. Existing audit and schedule execution history remain.

Missing Agent: `404 / 40401`.

## 7. Invoke Agent

`POST /api/v1/agents/{name}/invoke`

The existing request and response contract remains unchanged.

## AgentView

```json
{
  "name": "weather-daily",
  "description": "每日天气",
  "instructions": "你是每日天气助手。",
  "provider": {"name": "minimax", "model": "MiniMax-M2.7", "baseUrl": "https://api.minimaxi.com/v1", "temperature": 0.3},
  "tools": ["http_get", "notify"],
  "mcpServers": [],
  "notifyChannels": [{"name": "team", "type": "webhook"}],
  "schedules": [{"id": "weather-morning", "cron": "0 0 8 * * *", "zone": "Asia/Shanghai", "message": "查询天气并推送建议。"}],
  "sourcePath": "agents/weather-daily/AGENT.md"
}
```

`AgentView` never includes provider `apiKey` or notification channel secret config values.

## 8. Workspace Tree

`GET /api/v1/workspace/tree`

Success `200`: `ApiResponse<FileNode[]>` with exactly the available `agents` and `archive` roots, each recursively sorted by name.

```json
[
  {
    "name": "agents",
    "path": "agents",
    "type": "DIRECTORY",
    "readable": false,
    "children": [
      {
        "name": "weather-daily",
        "path": "agents/weather-daily",
        "type": "DIRECTORY",
        "readable": false,
        "children": [
          {"name": "AGENT.md", "path": "agents/weather-daily/AGENT.md", "type": "FILE", "readable": true, "children": []}
        ]
      }
    ]
  }
]
```

Unreadable entries and symbolic links escaping `.oryxos/` are not traversed.

## 9. Workspace File

`GET /api/v1/workspace/file?path=agents/weather-daily/AGENT.md`

Success `200`: `ApiResponse<String>` containing UTF-8 text.

Errors:

- `400 / 40000`: blank path, absolute path, directory path, binary/non-text file, normalized traversal, or symbolic-link escape.
- `404 / 40400`: in-bound file does not exist.

The endpoint is read-only and has no write variant.

## Unified Error Envelope

```json
{
  "code": 40002,
  "message": "Agent definition is invalid: provider [missing] is not registered",
  "data": null,
  "timestamp": 1789276896829
}
```

All errors pass through the existing global exception handling path. New business codes remain within the existing 400/404 status categories:

| Code | Meaning |
|---:|---|
| 40001 | Agent already exists |
| 40002 | Agent definition invalid |
| 40003 | Generated Agent draft invalid |
| 40401 | Agent/Profile not found (existing code) |

## 10. Agent Memory View

`GET /api/v1/memory?agent=weather-daily`

Success `200`: existing `ApiResponse<MemoryView>` envelope. `content` contains only `weather-daily` entries plus unmarked historical shared entries. Internal `[agent:weather-daily]` markers are removed from the response, and entries belonging to other Agents are excluded.

Omitting `agent` preserves the existing global memory response for compatibility. The endpoint remains read-only; Agent writes still occur through `save_memory`, which resolves the active Agent from `ProfileContext` without changing the Tool schema.
