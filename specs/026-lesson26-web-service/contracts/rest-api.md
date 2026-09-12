# REST API Contract: Lesson 26 Web Service

## Common Rules

- Base path: `/api/v1`
- Content type: `application/json`
- Success and error bodies use the existing four-field `ApiResponse` envelope.
- Message `content` must be non-blank and no longer than 32,768 characters.
- Stateful session detail returns at most the newest 100 messages.
- Agent processing waits at most 60 seconds before returning HTTP 504.
- Core-stage CORS allows all origins for browser-based debugging.

Example success envelope:

```json
{
  "code": 0,
  "message": "Success",
  "data": {},
  "timestamp": 1789182000000
}
```

Example unexpected error envelope:

```json
{
  "code": 50000,
  "message": "Internal server error",
  "data": null,
  "timestamp": 1789182000000
}
```

The 500 message is fixed and must not contain the original exception message, a local path, a database URL, or a stack trace.

## Endpoint Inventory

| # | Method | Path | Purpose |
|---:|---|---|---|
| 1 | POST | `/api/v1/sessions` | Create or resolve the stateful Web session for a user and Profile |
| 2 | GET | `/api/v1/sessions` | List session summaries, newest activity first |
| 3 | POST | `/api/v1/sessions/{id}/messages` | Send one message through the shared Agent engine |
| 4 | GET | `/api/v1/sessions/{id}` | Read session details and the newest 100 messages |
| 5 | DELETE | `/api/v1/sessions/{id}` | Archive a session |
| 6 | POST | `/api/v1/agents/{name}/invoke` | Invoke an Agent once without exposing a reusable session |
| 7 | GET | `/api/v1/profiles` | List safe Profile summaries |
| 8 | GET | `/api/v1/memory` | Read long-term memory |
| 9 | GET | `/api/v1/tools` | List Tool metadata and schemas |
| 10 | GET | `/api/v1/health` | Read service health |
| 11 | GET | `/api/v1/info` | Read runtime and Provider status |

## Session Endpoints

### POST /api/v1/sessions

Request:

```json
{
  "profileName": "ops-agent",
  "userId": "business-user-1"
}
```

The channel is fixed to `web`. The response data is a `SessionDetail` with an empty or existing history. Blank fields produce HTTP 400; an unknown Profile produces HTTP 404.

### GET /api/v1/sessions

Response data is an array of `SessionSummary`, ordered by `lastActiveAt` descending. Empty storage returns `[]`.

### POST /api/v1/sessions/{id}/messages

Request:

```json
{
  "content": "Summarize the current incident."
}
```

Success data:

```json
{
  "sessionId": "web:business-user-1:ops-agent",
  "reply": "..."
}
```

Missing or archived sessions return HTTP 404. Invalid content returns HTTP 400 without calling `AgentService.process`. Provider unavailability returns 503; the 60-second boundary returns 504.

### GET /api/v1/sessions/{id}

Response data is `SessionDetail`. Messages contain `role`, `content`, and optional `toolCallId`; only the newest 100 are returned.

### DELETE /api/v1/sessions/{id}

An existing session transitions to `ARCHIVED`. Missing sessions return HTTP 404. A repeated archive is idempotent and returns success with the archived representation.

## Agent Endpoint

### POST /api/v1/agents/{name}/invoke

Request:

```json
{
  "content": "Generate today's operations brief."
}
```

Success data:

```json
{
  "reply": "..."
}
```

The server resolves `name` through `ProfileRegistry`, creates an internal one-time session and calls the same `AgentService.process` path used by stateful sessions. Unknown Agents return 404; Provider failure returns 503; timeout returns 504.

## Read-Only Discovery Endpoints

### GET /api/v1/profiles

Each item contains `name`, `description`, `provider`, `model`, and `tools`. Credentials and notify destinations are excluded.

### GET /api/v1/memory

Response data:

```json
{
  "content": "## 核心记忆\n\n## 归档记忆\n"
}
```

### GET /api/v1/tools

Each item contains `name`, `description`, and `inputSchema`. This endpoint does not execute tools.

### GET /api/v1/health

Response data contains `status: "UP"` plus service version and response time.

### GET /api/v1/info

Response data contains application name/version, Java version, and safe Provider status entries:

```json
{
  "name": "OryxOS",
  "version": "0.1.0-SNAPSHOT",
  "javaVersion": "21",
  "providers": [
    {
      "name": "mock",
      "type": "MOCK",
      "defaultModel": "mock-model",
      "supportedModels": ["mock-model"],
      "available": true
    }
  ]
}
```

## Error Mapping

| HTTP status | Meaning | Example internal code |
|---:|---|---:|
| 400 | Validation or malformed request | 40000 |
| 404 | Session, Agent, Profile, Provider, route, or resource missing | 40400/40401/40402/40410 |
| 500 | Unexpected internal failure | 50000 |
| 503 | Provider temporarily unavailable | 50310 |
| 504 | Agent/Provider invocation exceeded its deadline | 50400/50410 |

## Admin Console Contract

- Entry point: `/admin` (redirect or forward to `/admin/index.html` is acceptable).
- Navigation labels: sessions, profiles, tools, memory, runtime status.
- Data sources: `GET /sessions`, `GET /profiles`, `GET /tools`, `GET /memory`, `GET /info` under the common base path.
- The page contains no create, edit, delete, invoke, archive, or Memory-write controls.
- Failed requests display the envelope's `message`; navigation remains usable.
