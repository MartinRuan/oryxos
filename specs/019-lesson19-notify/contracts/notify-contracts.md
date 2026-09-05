# Interface Contracts: 第19节 Notify 模块

## 1. NotifyChannelAdapter 接口

```java
package com.oryxos.tool.notify;

/**
 * 出站通知渠道适配器契约.
 */
public interface NotifyChannelAdapter {

  /**
   * 将指定文本内容发送到目标渠道.
   *
   * @param target 通知目标及渠道配置
   * @param content 待推送的消息文本
   * @throws RuntimeException 当网络通信失败或远端返回错误时抛出
   */
  void send(NotifyTarget target, String content);
}
```

---

## 2. Sandbox.enforce 契约

```java
package com.oryxos.tool.sandbox;

/**
 * 应用层沙箱接口契约.
 */
public interface Sandbox {

  /**
   * 校验动作是否在白名单允许范围内.
   *
   * @param target 目标对象
   * @return true 若允许执行
   */
  boolean check(String target);

  /**
   * 强制执行沙箱安全校验，若违规抛出异常.
   *
   * @param action 沙箱动作描述
   * @throws RuntimeException 若动作被沙箱拒绝
   */
  void enforce(SandboxAction action);
}
```

---

## 3. NotifyTools 内置工具契约

```java
package com.oryxos.tool.builtin;

import com.oryxos.core.model.ToolResult;
import org.springframework.ai.tool.annotation.Tool;

public class NotifyTools {

  /**
   * 把一条消息推送到当前 Agent 配置好的通知渠道.
   *
   * @param content 待推送的消息内容
   * @param channel 可选通知渠道名称，缺省时使用首个配置的渠道
   * @return 工具执行结果（ToolResult.success / failure）
   */
  @Tool(description = "把一条消息推送到当前 Agent 配置好的通知渠道")
  public ToolResult notify(String content, String channel);
}
```
