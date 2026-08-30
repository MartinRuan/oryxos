# Data Model: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

## 1. 领域模型 (Core Domain Models)

### `Session`
```java
public class Session {
    private String id;
    private String profileName;
    private String channel;
    private String userId;
    private List<ChatMessage> messages = new ArrayList<>();
    private String status = "ACTIVE";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    public void append(String userMessage);
    public void append(ChatMessage message);
    public void append(ChatResponse response);
    public void appendToolResult(ToolCallIntent call, ToolResult result);
}
```

### `ToolResult`
```java
public record ToolResult(
    boolean success,
    String content,
    String errorMessage,
    boolean retryable
) {
    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, false);
    }
    public static ToolResult failure(String errorMessage, boolean retryable) {
        return new ToolResult(false, null, errorMessage, retryable);
    }
}
```

### `ProfileContext`
```java
public final class ProfileContext {
    private static final ThreadLocal<Profile> CURRENT_PROFILE = new ThreadLocal<>();

    public static void set(Profile profile) {
        CURRENT_PROFILE.set(profile);
    }
    public static Profile get() {
        return CURRENT_PROFILE.get();
    }
    public static Profile current() {
        return CURRENT_PROFILE.get();
    }
    public static void clear() {
        CURRENT_PROFILE.remove();
    }
}
```

---

## 2. 关系数据库表结构 (SQLite)

### `tool_invocations` 表
```sql
CREATE TABLE IF NOT EXISTS tool_invocations (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    input_json TEXT,
    result_json TEXT,
    success BOOLEAN NOT NULL DEFAULT 1,
    error_message TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_id ON tool_invocations(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_tool_name ON tool_invocations(tool_name);
```

### JPA 实体 `ToolInvocationEntity`
- `id`: UUID 或特定前缀字符串
- `sessionId`: 会话 ID
- `toolName`: 工具名称
- `inputJson`: 工具调用入参 JSON 字符串
- `resultJson`: 工具返回结果 JSON 或文本
- `success`: 是否执行成功
- `errorMessage`: 异常错误堆栈或简要提示
- `durationMs`: 执行耗时毫秒
- `createdAt`: 创建时间
