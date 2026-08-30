package com.oryxos.core.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 会话领域实体. 维护一次对话的完整元数据、状态以及全量消息序列（包含用户消息、LLM 思考与回复、工具调用与回填结果）.
 *
 * @author oryxos
 */
public class Session implements Serializable {

  private static final long serialVersionUID = 1L;

  private String id;
  private String profileName;
  private String channel;
  private String userId;
  private List<ChatMessage> messages;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime lastActiveAt;
  private LocalDateTime archivedAt;

  /** 默认无参构造器. */
  public Session() {
    this.id = "session-" + UUID.randomUUID().toString().replace("-", "");
    this.channel = "cli";
    this.status = "ACTIVE";
    this.messages = new ArrayList<>();
    this.createdAt = LocalDateTime.now();
    this.lastActiveAt = LocalDateTime.now();
  }

  /**
   * 基于必要参数构造会话实体.
   *
   * @param id 会话唯一标识
   * @param profileName 绑定的 Profile 名称
   * @param channel 接入渠道名称
   * @param userId 触发用户标识
   */
  public Session(String id, String profileName, String channel, String userId) {
    this.id = id != null ? id : "session-" + UUID.randomUUID().toString().replace("-", "");
    this.profileName = profileName;
    this.channel = channel != null ? channel : "cli";
    this.userId = userId;
    this.status = "ACTIVE";
    this.messages = new ArrayList<>();
    this.createdAt = LocalDateTime.now();
    this.lastActiveAt = LocalDateTime.now();
  }

  /**
   * 追加一条纯文本用户消息.
   *
   * @param userMessage 用户输入内容
   */
  public void append(String userMessage) {
    if (userMessage != null && !userMessage.isBlank()) {
      append(ChatMessage.user(userMessage));
    }
  }

  /**
   * 追加一条通用 ChatMessage 消息.
   *
   * @param message 对话消息实体
   */
  public synchronized void append(ChatMessage message) {
    if (message != null) {
      this.messages.add(message);
      this.lastActiveAt = LocalDateTime.now();
    }
  }

  /**
   * 追加大模型 ChatResponse 响应为助手消息.
   *
   * @param response 大模型响应实体
   */
  public synchronized void append(ChatResponse response) {
    if (response != null) {
      if (response.hasToolCalls()) {
        append(ChatMessage.assistant(response.getContent(), response.getToolCalls()));
      } else {
        append(ChatMessage.assistant(response.getContent()));
      }
    }
  }

  /**
   * 追加工具执行结果消息.
   *
   * @param result 工具执行结果
   */
  public synchronized void appendToolResult(ToolResult result) {
    if (result != null) {
      String payload = result.isSuccess() ? result.getContent() : result.getErrorMessage();
      append(ChatMessage.tool(null, payload != null ? payload : ""));
    }
  }

  /**
   * 基于工具调用意图追加工具执行结果消息.
   *
   * @param call 工具调用意图
   * @param result 工具执行结果
   */
  public synchronized void appendToolResult(ToolCallIntent call, ToolResult result) {
    String callId = call != null ? call.getId() : null;
    if (result != null) {
      String payload = result.isSuccess() ? result.getContent() : result.getErrorMessage();
      append(ChatMessage.tool(callId, payload != null ? payload : ""));
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public synchronized List<ChatMessage> getMessages() {
    return Collections.unmodifiableList(new ArrayList<>(messages));
  }

  public synchronized void setMessages(List<ChatMessage> messages) {
    this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(LocalDateTime lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public LocalDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(LocalDateTime archivedAt) {
    this.archivedAt = archivedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Session session = (Session) o;
    return Objects.equals(id, session.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Session{"
        + "id='"
        + id
        + '\''
        + ", profileName='"
        + profileName
        + '\''
        + ", channel='"
        + channel
        + '\''
        + ", userId='"
        + userId
        + '\''
        + ", status='"
        + status
        + '\''
        + ", messageCount="
        + (messages != null ? messages.size() : 0)
        + '}';
  }
}
