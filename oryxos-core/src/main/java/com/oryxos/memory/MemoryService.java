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
   * 加载全部长期记忆的安全只读视图.
   *
   * @return 长期记忆文本
   */
  String load();

  /**
   * 加载指定 Agent 的长期记忆以及兼容的历史共享记忆.
   *
   * @param profileName Agent Profile 名称
   * @return 与 Agent 关联的长期记忆文本
   */
  String load(String profileName);

  /**
   * 组装待注入 System Prompt 的记忆上下文（核心记忆与会话必要信息，归档区不整体注入）.
   *
   * @param session 当前会话实体
   * @return 待注入的记忆上下文字符串
   */
  String buildContext(Session session);

  /**
   * 记住一条历史共享长期记忆（供无 Agent 上下文的兼容调用）.
   *
   * @param content 记忆文本内容
   * @param scope 存储作用域（CORE 或 ARCHIVAL）
   */
  void remember(String content, MemoryScope scope);

  /**
   * 为指定 Agent 记住一条长期记忆.
   *
   * @param content 记忆文本内容
   * @param scope 存储作用域（CORE 或 ARCHIVAL）
   * @param profileName Agent Profile 名称
   */
  void remember(String content, MemoryScope scope, String profileName);

  /**
   * 在全部归档记忆中按关键词检索（兼容无 Agent 上下文的调用）.
   *
   * @param keyword 检索关键词
   * @return 匹配命中的记忆条目集合，无命中返回空列表
   */
  List<String> recall(String keyword);

  /**
   * 在指定 Agent 与历史共享归档记忆中按关键词检索.
   *
   * @param keyword 检索关键词
   * @param profileName Agent Profile 名称
   * @return 匹配命中的记忆条目集合，无命中返回空列表
   */
  List<String> recall(String keyword, String profileName);
}
