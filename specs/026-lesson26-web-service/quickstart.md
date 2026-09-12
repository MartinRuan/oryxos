# Quickstart: Lesson 26 Web Service

## Prerequisites

- JDK 21
- The checked-in Maven wrapper
- A valid Agent directory under `.oryxos/agents/<name>/`
- No real Provider credential is required for slice tests or the read-only smoke paths

## 1. Run the Required Harness

```bash
./mvnw -pl oryxos-web -am test
./mvnw -pl oryxos-boot -am -Dtest=WebSmokeIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected outcomes:

- `SessionApiControllerTest` proves the 32KB boundary, missing-session 404 and exactly one shared-engine call.
- `GlobalExceptionHandlerTest` proves 400/404/500/503/504 mapping, the four-field envelope and no 500 detail leakage.
- `WebSmokeIT` starts the real aggregate context, reaches health, info, profiles, tools, memory and sessions without a real model, and verifies the 6-Controller/11-operation OpenAPI contract.

## 2. Run the Full Quality Gate

```bash
./mvnw clean verify
```

All modules, formatting checks, P3C/PMD, Checkstyle, SpotBugs, security checks and tests must pass.

## 3. Start the Service

```bash
./mvnw -pl oryxos-boot -am spring-boot:run
```

The default port is 8080 and virtual threads are enabled.

## 4. Exercise the 11-Endpoint Contract

Create a stateful Web session:

```bash
curl -sS -X POST http://localhost:8080/api/v1/sessions \
  -H 'Content-Type: application/json' \
  -d '{"profileName":"ops-agent","userId":"business-user-1"}'
```

Use the returned session ID for the message/detail/archive requests:

```bash
curl -sS http://localhost:8080/api/v1/sessions
curl -sS -X POST http://localhost:8080/api/v1/sessions/SESSION_ID/messages \
  -H 'Content-Type: application/json' \
  -d '{"content":"你好"}'
curl -sS http://localhost:8080/api/v1/sessions/SESSION_ID
curl -sS -X DELETE http://localhost:8080/api/v1/sessions/SESSION_ID
```

Invoke an Agent once:

```bash
curl -sS -X POST http://localhost:8080/api/v1/agents/ops-agent/invoke \
  -H 'Content-Type: application/json' \
  -d '{"content":"给出运行摘要"}'
```

Exercise the five read-only discovery paths:

```bash
curl -sS http://localhost:8080/api/v1/profiles
curl -sS http://localhost:8080/api/v1/memory
curl -sS http://localhost:8080/api/v1/tools
curl -sS http://localhost:8080/api/v1/health
curl -sS http://localhost:8080/api/v1/info
```

Every body has `code`, `message`, `data`, and `timestamp`.

## 5. Inspect Browser Surfaces

- Read-only admin console: `http://localhost:8080/admin`
- OpenAPI UI: `http://localhost:8080/swagger-ui`

Verify all five admin sections render real endpoint data and that no write controls appear.

## 6. Manual Fault and Concurrency Checks

- Submit 32,769 characters and verify HTTP 400 without an Agent call.
- Query an unknown session and verify HTTP 404.
- Inject Provider unavailability and verify HTTP 503.
- Inject an Agent operation lasting over 60 seconds and verify HTTP 504.
- Trigger an unexpected exception containing a database URL and verify the 500 response omits it.
- Send 200 concurrent one-time invocations and verify the virtual-thread server continues accepting requests.

Detailed request and response shapes are defined in [contracts/rest-api.md](contracts/rest-api.md).
