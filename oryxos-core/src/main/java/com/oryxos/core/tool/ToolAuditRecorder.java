package com.oryxos.core.tool;

/**
 * Tool 执行审计记录器契约.
 *
 * @author oryxos
 */
@FunctionalInterface
public interface ToolAuditRecorder {

  /**
   * 记录一次 Tool 执行调用明细（包含成败、耗时与错误信息）.
   *
   * @param sessionId 会话标识
   * @param toolName 工具名称
   * @param inputJson 输入入参 JSON
   * @param resultJson 结果 JSON
   * @param success 是否执行成功
   * @param errorMessage 错误原因（成功时为 null）
   * @param durationMs 执行耗时毫秒
   */
  void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs);
}
