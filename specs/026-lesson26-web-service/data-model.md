# Data Model: Lesson 26 - Web Service and Read-Only Admin Console

## 1. Existing Domain Entities

### Session

| Field | Type | Rules |
|---|---|---|
| `id` | String | Unique; generated from the existing channel/user/Profile rule for stateful Web sessions |
| `profileName` | String | Must identify an existing Profile |
| `channel` | String | `web` for stateful Web sessions |
| `userId` | String | Required when a stateful Web session is created |
| `messages` | List of ChatMessage | Stored in full; the detail response exposes only the newest 100 entries |
| `status` | String | `ACTIVE` or `ARCHIVED` |
| `createdAt` | Date-time | Set when created |
| `lastActiveAt` | Date-time | Updated when saved |
| `archivedAt` | Date-time | Set on archive |

State transition:

```text
ACTIVE --DELETE session--> ARCHIVED
```

An archived session remains queryable for audit/history but cannot accept new messages.

### Profile

Existing Agent execution configuration. The Web response exposes only name, description, selected provider/model and allowed Tool names. Provider credentials and notify channel secrets are excluded.

### Tool

Existing `OryxTool` metadata. The read model contains `name`, `description` and `inputSchema`; execution is not exposed by this feature.

### Provider

Existing explicitly registered provider descriptor. The read model contains `name`, `type`, `defaultModel`, `supportedModels` and `available`; `apiKey` and other credentials are excluded.

## 2. REST Request Models

### CreateSessionRequest

| Field | Type | Validation |
|---|---|---|
| `profileName` | String | Required, not blank, must resolve to an existing Profile |
| `userId` | String | Required, not blank |

### MessageRequest

| Field | Type | Validation |
|---|---|---|
| `content` | String | Required, not blank, UTF-8 length at most 32 × 1024 characters |

The same message shape is used by stateful message submission and one-time Agent invocation.

## 3. REST Response Models

All payloads are wrapped by the existing `ApiResponse<T>`:

| Field | Type | Rules |
|---|---|---|
| `code` | Integer | `0` on success; stable non-zero business code on failure |
| `message` | String | User-readable; generic for unexpected 500 errors |
| `data` | T or null | Endpoint-specific payload |
| `timestamp` | Long | Response creation time in epoch milliseconds |

### SessionSummary

`id`, `profileName`, `channel`, `userId`, `status`, `messageCount`, `createdAt`, `lastActiveAt`, `archivedAt`. It does not include message content.

### SessionDetail

All `SessionSummary` fields plus `messages`, containing at most the newest 100 `MessageView` values in original order.

### MessageView

`role`, `content`, and optional `toolCallId`. Internal Java type names and stack information are never serialized.

### AgentReply

`reply` only. The temporary invocation session identifier is kept internal.

### MemoryView

`content`, populated by the existing long-term memory load policy.

### SystemInfo

`name`, `version`, `javaVersion`, and a list of safe `ProviderView` objects.

## 4. Persistence Impact

No schema migration is required. The existing `sessions` table already stores every field required for collection and detail queries. Session listing adds an ordered repository query only; Memory remains in `.oryxos/memory/MEMORY.md`.
