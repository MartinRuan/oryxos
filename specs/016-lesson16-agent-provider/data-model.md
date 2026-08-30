# Data Model: 第16节 Agent Provider 与 Profile

## 1. Profile 领域模型 (`oryxos-core`)

`Profile` 实体包含全部元数据字段：

```java
public class Profile {
    private String name;
    private String description;
    private Identity identity;
    private ProviderConfig provider;
    private List<String> tools;
    private List<String> skills;
    private List<String> mcpServers;
    private List<ChannelConfig> channels;
    private List<String> notifyChannels;
    private List<ScheduleConfig> schedules;
    private List<String> bootstrap;
    private Settings settings;
    
    // 子对象: Identity (agent_name, prompt)
    // 子对象: ProviderConfig (name, model, temperature, apiKey, baseUrl)
    // 子对象: ChannelConfig (name, type, config)
    // 子对象: ScheduleConfig (cron, message, timezone)
    // 子对象: Settings (max_iterations, max_history_turns)
}
```

## 2. LLM 调用审计实体 `LlmCallEntity` (`oryxos-storage`)

对应 `llm_calls` 表：

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | BIGINT (PK, AutoIncrement) | 审计记录主键 |
| `sessionId` | VARCHAR(64) | 会话标识 (可空，针对无会话调用) |
| `provider` | VARCHAR(64) | Provider 名称 (如 deepseek, qwen) |
| `model` | VARCHAR(128) | 模型名称 (如 deepseek-chat) |
| `promptTokens` | INT | 输入 Token 消耗 |
| `completionTokens` | INT | 输出 Token 消耗 |
| `totalTokens` | INT | 总 Token 消耗 |
| `durationMs` | BIGINT | 调用耗时 (毫秒) |
| `success` | BOOLEAN | 调用是否成功 (true/false) |
| `errorMessage` | TEXT | 失败错误原因 (成功为 null) |
| `createdAt` | TIMESTAMP | 记录创建时间 |

## 3. SQLite DDL 建表脚本 (`schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(64),
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_tokens INTEGER DEFAULT 0,
    completion_tokens INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    duration_ms INTEGER DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT 1,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_session_id ON llm_calls(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_created_at ON llm_calls(created_at);
```
