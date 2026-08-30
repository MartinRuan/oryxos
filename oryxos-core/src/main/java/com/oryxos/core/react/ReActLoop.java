package com.oryxos.core.react;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;

/**
 * ReAct (Reason + Act) 核心推理调度循环契约.
 *
 * <p>输入会话和用户消息，在循环中反复进行“思考（调用 LLM）-> 行动（执行 Tool）-> 观察（回填结果）”， 直到模型给出最终答复或达到最大轮数限制.
 *
 * @author oryxos
 */
public interface ReActLoop {

  /**
   * 运行 ReAct 推理与工具循环.
   *
   * @param session 当前会话实体
   * @param userMessage 用户输入消息
   * @param profile 当前 Agent 配置
   * @return 最终响应文本
   */
  String run(Session session, String userMessage, Profile profile);
}
