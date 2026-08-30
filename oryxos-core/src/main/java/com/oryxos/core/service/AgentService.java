package com.oryxos.core.service;

import com.oryxos.core.model.Session;

/**
 * Agent 统一门面服务契约.
 *
 * <p>提供面向业务与上层渠道的统一会话消息处理入口，自动管理 ProfileContext 绑定与清理，调度 ReAct 循环，并保障会话状态落库.
 *
 * @author oryxos
 */
public interface AgentService {

  /**
   * 处理用户发送的一条消息并返回 Agent 的最终响应.
   *
   * @param session 会话领域对象
   * @param userMessage 用户输入文本
   * @return Agent 最终答复内容
   */
  String process(Session session, String userMessage);
}
