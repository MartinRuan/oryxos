package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 统一大模型对话响应对象.
 *
 * @author oryxos
 */
public class ChatResponse implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String provider;
  private final String model;
  private final String content;
  private final List<ToolCallIntent> toolCalls;
  private final FinishReason finishReason;
  private final TokenUsage usage;
  private final long durationMs;

  /**
   * 全参构造器.
   *
   * @param provider 提供商名称
   * @param model 模型型号
   * @param content 文本生成内容
   * @param toolCalls 工具调用指令列表
   * @param finishReason 结束原因
   * @param usage Token 统计
   * @param durationMs 耗时毫秒
   */
  public ChatResponse(
      String provider,
      String model,
      String content,
      List<ToolCallIntent> toolCalls,
      FinishReason finishReason,
      TokenUsage usage,
      long durationMs) {
    this.provider = Objects.requireNonNull(provider, "provider must not be null");
    this.model = model != null ? model : "";
    this.content = content != null ? content : "";
    this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : Collections.emptyList();
    this.finishReason = finishReason != null ? finishReason : FinishReason.STOP;
    this.usage = usage != null ? usage : TokenUsage.zero();
    this.durationMs = Math.max(0, durationMs);
  }

  /**
   * 快速构造纯文本回复.
   *
   * @param content 文本回复内容
   * @return ChatResponse 实例
   */
  public static ChatResponse of(String content) {
    return builder().provider("mock").model("mock-model").content(content).build();
  }

  /**
   * 创建 Builder 构建器.
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public String getContent() {
    return content;
  }

  public List<ToolCallIntent> getToolCalls() {
    return Collections.unmodifiableList(toolCalls);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }

  public FinishReason getFinishReason() {
    return finishReason;
  }

  public TokenUsage getUsage() {
    return usage;
  }

  public long getDurationMs() {
    return durationMs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatResponse that = (ChatResponse) o;
    return durationMs == that.durationMs
        && Objects.equals(provider, that.provider)
        && Objects.equals(model, that.model)
        && Objects.equals(content, that.content)
        && Objects.equals(toolCalls, that.toolCalls)
        && finishReason == that.finishReason
        && Objects.equals(usage, that.usage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, model, content, toolCalls, finishReason, usage, durationMs);
  }

  @Override
  public String toString() {
    return "ChatResponse{"
        + "provider='"
        + provider
        + '\''
        + ", model='"
        + model
        + '\''
        + ", content='"
        + content
        + '\''
        + ", toolCallsCount="
        + toolCalls.size()
        + ", finishReason="
        + finishReason
        + ", usage="
        + usage
        + ", durationMs="
        + durationMs
        + '}';
  }

  /** Builder 构造器. */
  public static final class Builder {
    private String provider;
    private String model;
    private String content;
    private List<ToolCallIntent> toolCalls = Collections.emptyList();
    private FinishReason finishReason = FinishReason.STOP;
    private TokenUsage usage = TokenUsage.zero();
    private long durationMs;

    public Builder provider(String provider) {
      this.provider = provider;
      return this;
    }

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    public Builder content(String content) {
      this.content = content;
      return this;
    }

    /**
     * 设置工具调用指令列表.
     *
     * @param toolCalls 工具调用列表
     * @return Builder 实例
     */
    public Builder toolCalls(List<ToolCallIntent> toolCalls) {
      this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : Collections.emptyList();
      return this;
    }

    public Builder finishReason(FinishReason finishReason) {
      this.finishReason = finishReason;
      return this;
    }

    public Builder usage(TokenUsage usage) {
      this.usage = usage;
      return this;
    }

    public Builder durationMs(long durationMs) {
      this.durationMs = durationMs;
      return this;
    }

    /**
     * 构建 ChatResponse 实例.
     *
     * @return ChatResponse 对象
     */
    public ChatResponse build() {
      String prov = provider != null ? provider : "unknown";
      String mdl = model != null ? model : "unknown-model";
      return new ChatResponse(prov, mdl, content, toolCalls, finishReason, usage, durationMs);
    }
  }
}
