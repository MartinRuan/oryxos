# Data Model: 第19节 Notify 模块

## 1. NotifyTarget (值对象)

表示一次出站通知的目标渠道配置：

```java
package com.oryxos.tool.notify;

import java.util.Map;

/**
 * 出站通知目标值对象.
 *
 * @param channelType 渠道类型标识，如 "webhook", "email"
 * @param config 渠道配置字典，如 {"url": "https://..."}
 */
public record NotifyTarget(String channelType, Map<String, String> config) {
}
```

---

## 2. Sandbox 动作定义

```java
package com.oryxos.tool.sandbox;

/**
 * 沙箱动作类型枚举.
 */
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
```

```java
package com.oryxos.tool.sandbox;

/**
 * 沙箱动作描述值对象.
 *
 * @param type 动作类型
 * @param target 操作目标（如文件路径、命令、URL）
 */
public record SandboxAction(ActionType type, String target) {
}
```

---

## 3. Profile 中的 Notify 配置模型

```yaml
name: daily-tech-digest
description: 每日科技日报 Agent
provider:
  name: deepseek
  model: deepseek-chat
notify_channels:
  - name: team-webhook
    type: webhook
    url: ${TEAM_WEBHOOK_URL}
```

在 Java Profile 模型中映射为：

```java
public static class NotifyChannelConfig implements Serializable {
    private String name;
    private String type = "webhook";
    private Map<String, String> config = new HashMap<>();

    public String getUrl() {
        return config.get("url");
    }
}
```
