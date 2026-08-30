package com.oryxos.core.tool;

import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;

/**
 * 统一工具安全执行器契约.
 *
 * <p>负责在工具执行前执行沙箱校验，调度工具执行，并将成功/失败调用详情落库审计表 tool_invocations.
 *
 * @author oryxos
 */
public interface ToolExecutor {

  /**
   * 执行指定工具调用并记录调用审计.
   *
   * @param sessionId 会话标识
   * @param call 工具调用意图
   * @return 工具执行结果
   */
  ToolResult execute(String sessionId, ToolCallIntent call);
}
