package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 统一对话消息实体.
 *
 * @author oryxos
 */
public class ChatMessage implements Serializable {

  private static final long serialVersionUID = 1L;

  private final MessageType role;
  private final String content;
  private final List<ToolCallIntent> toolCalls;
  private final String toolCallId;

  /**
   * 构造对话消息实体.
   *
   * @param role 消息角色
   * @param content 文本内容
   * @param toolCalls 工具调用意图列表
   * @param toolCallId 工具调用响应关联 ID
   */
  public ChatMessage(
      MessageType role, String content, List<ToolCallIntent> toolCalls, String toolCallId) {
    this.role = Objects.requireNonNull(role, "role must not be null");
    this.content = content != null ? content : "";
    this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : Collections.emptyList();
    this.toolCallId = toolCallId;
  }

  /**
   * 构造系统提示词消息.
   *
   * @param content 提示词内容
   * @return ChatMessage 实例
   */
  public static ChatMessage system(String content) {
    return new ChatMessage(MessageType.SYSTEM, content, null, null);
  }

  /**
   * 构造用户消息.
   *
   * @param content 用户输入内容
   * @return ChatMessage 实例
   */
  public static ChatMessage user(String content) {
    return new ChatMessage(MessageType.USER, content, null, null);
  }

  /**
   * 构造助手回复消息.
   *
   * @param content 助手输出文本
   * @return ChatMessage 实例
   */
  public static ChatMessage assistant(String content) {
    return new ChatMessage(MessageType.ASSISTANT, content, null, null);
  }

  /**
   * 构造包含工具调用意图的助手消息.
   *
   * @param content 思考或说明文本
   * @param toolCalls 工具调用列表
   * @return ChatMessage 实例
   */
  public static ChatMessage assistant(String content, List<ToolCallIntent> toolCalls) {
    return new ChatMessage(MessageType.ASSISTANT, content, toolCalls, null);
  }

  /**
   * 构造工具执行结果回填消息.
   *
   * @param toolCallId 工具调用关联 ID
   * @param resultJson 工具执行结果 JSON
   * @return ChatMessage 实例
   */
  public static ChatMessage tool(String toolCallId, String resultJson) {
    return new ChatMessage(MessageType.TOOL, resultJson, null, toolCallId);
  }

  public MessageType getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public List<ToolCallIntent> getToolCalls() {
    return Collections.unmodifiableList(toolCalls);
  }

  public String getToolCallId() {
    return toolCallId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatMessage that = (ChatMessage) o;
    return role == that.role
        && Objects.equals(content, that.content)
        && Objects.equals(toolCalls, that.toolCalls)
        && Objects.equals(toolCallId, that.toolCallId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, content, toolCalls, toolCallId);
  }

  @Override
  public String toString() {
    return "ChatMessage{"
        + "role="
        + role
        + ", content='"
        + content
        + '\''
        + ", toolCalls="
        + toolCalls
        + ", toolCallId='"
        + toolCallId
        + '\''
        + '}';
  }
}
