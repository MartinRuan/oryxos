package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * LLM 调用审计记录持久化实体. 映射数据库表 llm_calls.
 *
 * @author oryxos
 */
@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

  private static final String DEFAULT_SESSION = "default_session";
  private static final String DEFAULT_UNKNOWN = "unknown";

  @Id
  @Column(name = "id", length = 64, nullable = false)
  private String id;

  @Column(name = "session_id", length = 64, nullable = false)
  private String sessionId;

  @Column(name = "provider", length = 32, nullable = false)
  private String provider;

  @Column(name = "model", length = 64, nullable = false)
  private String model;

  @Column(name = "prompt_tokens", nullable = false)
  private int promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private int completionTokens;

  @Column(name = "total_tokens", nullable = false)
  private int totalTokens;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message", length = 2048)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 默认无参构造器. */
  public LlmCallEntity() {
    this.id = UUID.randomUUID().toString();
    this.success = true;
    this.createdAt = Instant.now();
  }

  /**
   * 全参构造器.
   *
   * @param id 唯一 ID
   * @param sessionId 会话 ID
   * @param provider 提供商名称
   * @param model 模型名称
   * @param promptTokens 输入 Token 数
   * @param completionTokens 输出 Token 数
   * @param totalTokens 总 Token 数
   * @param durationMs 耗时毫秒
   * @param success 是否调用成功
   * @param errorMessage 错误原因
   * @param createdAt 创建时间
   */
  public LlmCallEntity(
      String id,
      String sessionId,
      String provider,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long durationMs,
      boolean success,
      String errorMessage,
      Instant createdAt) {
    this.id = id != null ? id : UUID.randomUUID().toString();
    this.sessionId = sessionId != null ? sessionId : DEFAULT_SESSION;
    this.provider = provider != null ? provider : DEFAULT_UNKNOWN;
    this.model = model != null ? model : DEFAULT_UNKNOWN;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.totalTokens = totalTokens;
    this.durationMs = durationMs;
    this.success = success;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getPromptTokens() {
    return promptTokens;
  }

  public void setPromptTokens(int promptTokens) {
    this.promptTokens = promptTokens;
  }

  public int getCompletionTokens() {
    return completionTokens;
  }

  public void setCompletionTokens(int completionTokens) {
    this.completionTokens = completionTokens;
  }

  public int getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(int totalTokens) {
    this.totalTokens = totalTokens;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LlmCallEntity that = (LlmCallEntity) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
