# Contracts: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

## 1. ReActLoop Contract
```java
package com.oryxos.core.react;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;

public interface ReActLoop {
    /**
     * 执行 ReAct 推理与工具循环.
     *
     * @param session 当前会话
     * @param userMessage 用户最新输入
     * @param profile 当前 Agent 配置
     * @return 最终响应文本
     */
    String run(Session session, String userMessage, Profile profile);
}
```

## 2. PromptBuilder Contract
```java
package com.oryxos.core.prompt;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;

public interface PromptBuilder {
    /**
     * 组装本轮 LLM 调用的完整 Prompt 上下文.
     *
     * @param session 当前会话
     * @param profile 当前 Agent 配置
     * @return 结构化对话请求
     */
    ChatRequest build(Session session, Profile profile);
}
```

## 3. ToolExecutor Contract
```java
package com.oryxos.core.tool;

import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;

public interface ToolExecutor {
    /**
     * 执行工具调用并记录调用审计.
     *
     * @param sessionId 会话标识
     * @param call 工具调用意图
     * @return 工具执行结果
     */
    ToolResult execute(String sessionId, ToolCallIntent call);
}
```

## 4. AgentService Contract
```java
package com.oryxos.core.service;

import com.oryxos.core.model.Session;

public interface AgentService {
    /**
     * 处理一次 Agent 对话消息，统一编排 Profile 上下文、ReAct 循环与持久化.
     *
     * @param session 会话对象
     * @param userMessage 用户消息
     * @return Agent 最终答复
     */
    String process(Session session, String userMessage);
}
```

## 5. ContextLoader Contract
```java
package com.oryxos.core.context;

import com.oryxos.core.model.Profile;

public interface ContextLoader {
    /**
     * 加载 Agent Bootstrap 文件与 Skill 描述拼接为系统前置提示词.
     *
     * @param profile Agent Profile
     * @return 前置上下文文本
     */
    String loadContext(Profile profile);
}
```
