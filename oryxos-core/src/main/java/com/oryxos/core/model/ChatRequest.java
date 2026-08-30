package com.oryxos.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 统一大模型对话请求对象.
 *
 * @author oryxos
 */
public final class ChatRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String provider;
  private final String model;
  private final List<ChatMessage> messages;
  private final List<ToolDefinition> tools;
  private final Double temperature;
  private final Integer maxTokens;
  private final String sessionId;

  /**
   * 全参构造器.
   *
   * @param provider 目标提供商
   * @param model 目标模型型号
   * @param messages 消息列表
   * @param tools 工具列表
   * @param temperature 采样温度
   * @param maxTokens 最大 Token 数
   * @param sessionId 会话 ID
   */
  public ChatRequest(
      String provider,
      String model,
      List<ChatMessage> messages,
      List<ToolDefinition> tools,
      Double temperature,
      Integer maxTokens,
      String sessionId) {
    this.provider = provider != null ? provider : "";
    this.model = model;
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be null or empty");
    }
    this.messages = List.copyOf(messages);
    this.tools = tools != null ? List.copyOf(tools) : Collections.emptyList();
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.sessionId = sessionId;
  }

  /**
   * 创建 Builder 构建器.
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 基于已有请求创建 Builder 构建器.
   *
   * @param copy 待复制的请求
   * @return Builder 实例
   */
  public static Builder builder(ChatRequest copy) {
    Builder builder = new Builder();
    if (copy != null) {
      builder.provider = copy.provider;
      builder.model = copy.model;
      if (copy.messages != null) {
        builder.messages.addAll(copy.messages);
      }
      if (copy.tools != null) {
        builder.tools.addAll(copy.tools);
      }
      builder.temperature = copy.temperature;
      builder.maxTokens = copy.maxTokens;
      builder.sessionId = copy.sessionId;
    }
    return builder;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public List<ChatMessage> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  public List<ToolDefinition> getTools() {
    return Collections.unmodifiableList(tools);
  }

  public Double getTemperature() {
    return temperature;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public String getSessionId() {
    return sessionId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatRequest that = (ChatRequest) o;
    return Objects.equals(provider, that.provider)
        && Objects.equals(model, that.model)
        && Objects.equals(messages, that.messages)
        && Objects.equals(tools, that.tools)
        && Objects.equals(temperature, that.temperature)
        && Objects.equals(maxTokens, that.maxTokens)
        && Objects.equals(sessionId, that.sessionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, model, messages, tools, temperature, maxTokens, sessionId);
  }

  @Override
  public String toString() {
    return "ChatRequest{"
        + "provider='"
        + provider
        + '\''
        + ", model='"
        + model
        + '\''
        + ", messagesCount="
        + messages.size()
        + ", toolsCount="
        + tools.size()
        + ", temperature="
        + temperature
        + ", maxTokens="
        + maxTokens
        + ", sessionId='"
        + sessionId
        + '\''
        + '}';
  }

  /** Builder 构造器. */
  public static final class Builder {
    private String provider;
    private String model;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<ToolDefinition> tools = new ArrayList<>();
    private Double temperature;
    private Integer maxTokens;
    private String sessionId;

    public Builder provider(String provider) {
      this.provider = provider;
      return this;
    }

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    /**
     * 追加单条用户提示词文本消息.
     *
     * @param prompt 用户文本
     * @return Builder 实例
     */
    public Builder prompt(String prompt) {
      if (prompt != null && !prompt.isBlank()) {
        this.messages.add(ChatMessage.user(prompt));
      }
      return this;
    }

    /**
     * 追加单条对话消息.
     *
     * @param message 对话消息
     * @return Builder 实例
     */
    public Builder message(ChatMessage message) {
      if (message != null) {
        this.messages.add(message);
      }
      return this;
    }

    /**
     * 追加多条对话消息.
     *
     * @param messages 消息列表
     * @return Builder 实例
     */
    public Builder messages(List<ChatMessage> messages) {
      if (messages != null) {
        this.messages.addAll(messages);
      }
      return this;
    }

    /**
     * 追加单个工具定义.
     *
     * @param tool 工具定义
     * @return Builder 实例
     */
    public Builder tool(ToolDefinition tool) {
      if (tool != null) {
        this.tools.add(tool);
      }
      return this;
    }

    /**
     * 追加多个工具定义.
     *
     * @param tools 工具列表
     * @return Builder 实例
     */
    public Builder tools(List<ToolDefinition> tools) {
      if (tools != null) {
        this.tools.addAll(tools);
      }
      return this;
    }

    public Builder temperature(Double temperature) {
      this.temperature = temperature;
      return this;
    }

    public Builder maxTokens(Integer maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder sessionId(String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    /**
     * 构建 ChatRequest 实例.
     *
     * @return ChatRequest 对象
     */
    public ChatRequest build() {
      return new ChatRequest(provider, model, messages, tools, temperature, maxTokens, sessionId);
    }
  }
}
