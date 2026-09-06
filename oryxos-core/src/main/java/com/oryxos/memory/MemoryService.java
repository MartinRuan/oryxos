package com.oryxos.memory;

import com.oryxos.core.model.Session;
import java.util.List;

/**
 * 记忆系统统一门面服务契约.
 *
 * <p>收口会话记忆与长期记忆，为 PromptBuilder 提供核心上下文，并为 Agent MemoryTools 提供读写支持.
 *
 * @author OryxOS Team
 */
public interface MemoryService {

  /**
   * 组装待注入 System Prompt 的记忆上下文（核心记忆与会话必要信息，归档区不整体注入）.
   *
   * @param session 当前会话实体
   * @return 待注入的记忆上下文字符串
   */
  String buildContext(Session session);

  /**
   * 记住一条长期记忆（供 save_memory 工具调用）.
   *
   * @param content 记忆文本内容
   * @param scope 存储作用域（CORE 或 ARCHIVAL）
   */
  void remember(String content, MemoryScope scope);

  /**
   * 按关键词检索归档记忆（供 recall_memory 工具调用）.
   *
   * @param keyword 检索关键词
   * @return 匹配命中的记忆条目集合，无命中返回空列表
   */
  List<String> recall(String keyword);
}
