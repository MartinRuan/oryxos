package com.oryxos.core.model;

/**
 * 模型推理生成结束原因枚举.
 *
 * @author oryxos
 */
public enum FinishReason {

  /** 正常生成完成（遇到终止标记或自然结束）. */
  STOP,

  /** 模型请求调用一个或多个外部工具 (Function Calling). */
  TOOL_CALLS,

  /** 达到最大 Token 生成上限被截断. */
  LENGTH,

  /** 触发内容安全策略被过滤. */
  CONTENT_FILTER,

  /** 未知或其他结束原因. */
  UNKNOWN;

  private static final String KEYWORD_TOOL = "tool";
  private static final String KEYWORD_FUNCTION = "function";
  private static final String KEYWORD_STOP = "stop";
  private static final String KEYWORD_LENGTH = "length";
  private static final String KEYWORD_MAX_TOKENS = "max_tokens";

  /**
   * 从字符串反向匹配枚举.
   *
   * @param text 结束原因字符串
   * @return 对应的 FinishReason
   */
  public static FinishReason fromString(String text) {
    if (text == null) {
      return UNKNOWN;
    }
    String trimmed = text.trim();
    for (FinishReason reason : values()) {
      if (reason.name().equalsIgnoreCase(trimmed)) {
        return reason;
      }
    }
    if (containsIgnoreCase(trimmed, KEYWORD_TOOL)
        || containsIgnoreCase(trimmed, KEYWORD_FUNCTION)) {
      return TOOL_CALLS;
    }
    if (containsIgnoreCase(trimmed, KEYWORD_STOP)) {
      return STOP;
    }
    if (containsIgnoreCase(trimmed, KEYWORD_LENGTH)
        || containsIgnoreCase(trimmed, KEYWORD_MAX_TOKENS)) {
      return LENGTH;
    }
    return UNKNOWN;
  }

  private static boolean containsIgnoreCase(String source, String target) {
    if (source == null || target == null) {
      return false;
    }
    final int length = target.length();
    if (length == 0) {
      return true;
    }
    for (int i = source.length() - length; i >= 0; i--) {
      if (source.regionMatches(true, i, target, 0, length)) {
        return true;
      }
    }
    return false;
  }
}
