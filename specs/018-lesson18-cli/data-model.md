# Data Model & Schema: 第18节 CLI 与会话持久化

## 1. 实体模型：Session 领域模型与持久化实体

### `Session` 领域模型 (oryxos-core)
```java
package com.oryxos.core.model;

public class Session implements Serializable {
    private String id;               // session_id (channel:userId:profileName)
    private String profileName;      // 关联 Profile
    private String channel;          // 接入渠道 (cli, web, scheduler 等)
    private String userId;           // 用户标识 (如当前 OS user 或 web user)
    private List<ChatMessage> messages; // 对话消息序列
    private String status;           // ACTIVE / ARCHIVED
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime archivedAt;
}
```

### `SessionEntity` 持久化实体 (oryxos-storage)
```java
package com.oryxos.storage.entity;

@Entity
@Table(name = "sessions")
public class SessionEntity {
    @Id
    @Column(name = "session_id", length = 128, nullable = false)
    private String sessionId;

    @Column(name = "profile_name", length = 64, nullable = false)
    private String profileName;

    @Column(name = "channel", length = 32, nullable = false)
    private String channel;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Lob
    @Column(name = "messages_json", columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
```

---

## 2. SQLite DDL 建表脚本 (`sessions`)

```sql
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    profile_name VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    messages_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_profile_name ON sessions(profile_name);
CREATE INDEX IF NOT EXISTS idx_sessions_channel_user ON sessions(channel, user_id);
```
