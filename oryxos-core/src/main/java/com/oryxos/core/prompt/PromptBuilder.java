package com.oryxos.core.prompt;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;

/**
 * 结构化 Prompt 组装器契约.
 *
 * <p>按顺序组装 System Prompt (含 Bootstrap、Skill、当前日期时间)、长期记忆、按窗口截断的历史消息及可用工具 Schema.
 *
 * @author oryxos
 */
public interface PromptBuilder {

  /**
   * 组装本轮调用的完整 ChatRequest.
   *
   * @param session 当前会话实体
   * @param profile 当前 Agent 配置
   * @return 组装好的统一对话请求
   */
  ChatRequest build(Session session, Profile profile);
}
