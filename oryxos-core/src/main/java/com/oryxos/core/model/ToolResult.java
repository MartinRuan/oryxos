package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 工具执行统一输出结果封装.
 *
 * @author oryxos
 */
public class ToolResult implements Serializable {

  private static final long serialVersionUID = 1L;

  private final boolean success;
  private final String content;
  private final String errorMessage;
  private final boolean retryable;

  /**
   * 构造工具执行结果.
   *
   * @param success 是否执行成功
   * @param content 成功输出内容
   * @param errorMessage 失败错误描述
   * @param retryable 失败时是否可重试
   */
  public ToolResult(boolean success, String content, String errorMessage, boolean retryable) {
    this.success = success;
    this.content = content != null ? content : "";
    this.errorMessage = errorMessage;
    this.retryable = retryable;
  }

  /**
   * 创建成功结果实例.
   *
   * @param content 成功输出内容
   * @return ToolResult
   */
  public static ToolResult success(String content) {
    return new ToolResult(true, content, null, false);
  }

  /**
   * 创建失败结果实例（默认不可重试）.
   *
   * @param errorMessage 错误描述
   * @return ToolResult
   */
  public static ToolResult failure(String errorMessage) {
    return new ToolResult(false, null, errorMessage, false);
  }

  /**
   * 创建失败结果实例（显式指定是否可重试）.
   *
   * @param errorMessage 错误描述
   * @param retryable 是否可重试
   * @return ToolResult
   */
  public static ToolResult failure(String errorMessage, boolean retryable) {
    return new ToolResult(false, null, errorMessage, retryable);
  }

  public boolean isSuccess() {
    return success;
  }

  public String getContent() {
    return content;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public boolean isRetryable() {
    return retryable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolResult that = (ToolResult) o;
    return success == that.success
        && retryable == that.retryable
        && Objects.equals(content, that.content)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, content, errorMessage, retryable);
  }

  @Override
  public String toString() {
    return "ToolResult{"
        + "success="
        + success
        + ", content='"
        + content
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", retryable="
        + retryable
        + '}';
  }
}
