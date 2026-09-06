# Contracts: 第22节 Memory 模块契约定义

## 1. MemoryService 门面接口契约

```java
package com.oryxos.memory;

import com.oryxos.core.model.Session;
import java.util.List;

/**
 * 记忆系统统一门面服务契约.
 * 收口会话记忆与长期记忆，供 ReAct 循环、PromptBuilder 及 MemoryTools 统一调用.
 */
public interface MemoryService {

  /**
   * 组装注入 Prompt 的记忆上下文（核心记忆 + 会话必要信息，归档记忆不整体注入）.
   *
   * @param session 当前会话实体
   * @return 待注入 System Prompt 的记忆上下文字符串
   */
  String buildContext(Session session);

  /**
   * 记住一条新记忆（供 save_memory 工具调用）.
   *
   * @param content 记忆文本内容
   * @param scope 目标存储作用域（CORE 或 ARCHIVAL）
   */
  void remember(String content, MemoryScope scope);

  /**
   * 按关键词检索归档记忆（供 recall_memory 工具调用）.
   *
   * @param keyword 检索关键词
   * @return 命中的记忆条目集合，无命中返回空列表
   */
  List<String> recall(String keyword);
}
```

## 2. LongTermMemory 长期记忆物理读写契约

```java
package com.oryxos.memory;

import java.util.List;

/**
 * 长期记忆物理存储组件.
 * 负责 MEMORY.md 文件的分区读写、截断与关键词检索，强制零缓存.
 */
public class LongTermMemory {

  /**
   * 向指定分区追加一条记忆条目.
   *
   * @param content 内容
   * @param scope 分区（CORE 或 ARCHIVAL）
   */
  public void append(String content, MemoryScope scope);

  /**
   * 加载长期记忆全貌（核心区全量 + 归档区截断后内容）.
   *
   * @return 拼接好的长期记忆文本
   */
  public String load();

  /**
   * 获取核心记忆全量内容（供 buildContext 使用）.
   *
   * @return 核心记忆文本
   */
  public String getCoreMemory();

  /**
   * 按关键词在归档区检索条目（不区分大小写）.
   *
   * @param keyword 关键词
   * @return 命中的条目列表
   */
  public List<String> recallByKeyword(String keyword);
}
```

## 3. MemoryTools 工具暴露契约

```java
package com.oryxos.memory;

import com.alibaba.cloud.ai.tool.annotation.Tool;
import com.alibaba.cloud.ai.tool.annotation.ToolParam;

/**
 * 长期记忆读写工具集.
 */
public class MemoryTools {

  @Tool(name = "save_memory", description = "记住一件值得长期记住的事")
  public String saveMemory(
      @ToolParam(description = "要记住的内容") String content,
      @ToolParam(description = "core 或 archival，不确定就填 archival") String scope);

  @Tool(name = "recall_memory", description = "按关键词检索长期记忆")
  public String recallMemory(@ToolParam(description = "检索关键词") String keyword);
}
```
