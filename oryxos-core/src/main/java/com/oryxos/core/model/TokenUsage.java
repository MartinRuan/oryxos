package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 模型调用 Token 消耗统计值对象.
 *
 * @author oryxos
 */
public class TokenUsage implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int promptTokens;
  private final int completionTokens;
  private final int totalTokens;

  /**
   * 全参构造器.
   *
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @param totalTokens 总 Token 数
   */
  public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.totalTokens = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
  }

  /**
   * 根据 prompt 和 completion token 构造用量对象.
   *
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @return TokenUsage 实例
   */
  public static TokenUsage of(int promptTokens, int completionTokens) {
    return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
  }

  /**
   * 构造零 Token 消耗用量对象.
   *
   * @return 零用量实例
   */
  public static TokenUsage zero() {
    return new TokenUsage(0, 0, 0);
  }

  public int getPromptTokens() {
    return promptTokens;
  }

  public int getCompletionTokens() {
    return completionTokens;
  }

  public int getTotalTokens() {
    return totalTokens;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TokenUsage that = (TokenUsage) o;
    return promptTokens == that.promptTokens
        && completionTokens == that.completionTokens
        && totalTokens == that.totalTokens;
  }

  @Override
  public int hashCode() {
    return Objects.hash(promptTokens, completionTokens, totalTokens);
  }

  @Override
  public String toString() {
    return "TokenUsage{"
        + "promptTokens="
        + promptTokens
        + ", completionTokens="
        + completionTokens
        + ", totalTokens="
        + totalTokens
        + '}';
  }
}
