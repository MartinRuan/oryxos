# Interface Contracts: 第18节 CLI 与 SessionManager

## 1. `SessionManager` 契约 (oryxos-core)

```java
package com.oryxos.core.session;

import com.oryxos.core.model.Session;
import java.util.Optional;

public interface SessionManager {

    /**
     * 根据渠道、用户与 Profile 获取或创建会话.
     * 会话 ID 格式由实现类统一拼接 (channel + ":" + user + ":" + profileName).
     */
    Session getOrCreate(String channel, String user, String profileName);

    /**
     * 根据会话 ID 查询会话.
     */
    Optional<Session> get(String sessionId);

    /**
     * 保存或更新会话状态与消息序列.
     */
    void save(Session session);

    /**
     * 归档指定会话.
     */
    void archive(String sessionId);
}
```

## 2. `CliChannel` 交互契约 (oryxos-channel-cli)

```java
package com.oryxos.channel.cli;

import com.oryxos.core.model.Session;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.io.InputStream;
import java.io.PrintStream;

public class CliChannel {

    public void startChat(String profileName, InputStream in, PrintStream out);

    public String getChannelName();
}
```

## 3. CLI 命令清单 (oryxos-cli)

1. `OryxCliCommand` (`oryxos` 主入口)
2. `ChatCommand` (`oryxos chat [--profile <name>]`)
3. `InitCommand` (`oryxos init`)
4. `StatusCommand` (`oryxos status`)
5. `ServeCommand` (`oryxos serve [--port <port>]`)
6. `GatewayCommand` (`oryxos gateway`)
7. `ProfileCommand` (`oryxos profile <list|create|show|delete>`)
8. `ProviderCommand` (`oryxos provider <list>`)
9. `ToolCommand` (`oryxos tool <list>`)
10. `SessionCommand` (`oryxos session <list>`)
