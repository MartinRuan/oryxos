package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tool 调用审计记录持久化实体. 映射数据库表 tool_invocations.
 *
 * @author oryxos
 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocationEntity {

  private static final String DEFAULT_SESSION = "default_session";
  private static final String DEFAULT_UNKNOWN = "unknown";

  @Id
  @Column(name = "id", length = 64, nullable = false)
  private String id;

  @Column(name = "session_id", length = 64, nullable = false)
  private String sessionId;

  @Column(name = "tool_name", length = 64, nullable = false)
  private String toolName;

  @Column(name = "input_json")
  private String inputJson;

  @Column(name = "result_json")
  private String resultJson;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 默认无参构造器. */
  public ToolInvocationEntity() {
    this.id = UUID.randomUUID().toString();
    this.success = true;
    this.createdAt = Instant.now();
  }

  /**
   * 全参构造器.
   *
   * @param id 唯一主键
   * @param sessionId 会话 ID
   * @param toolName 工具名称
   * @param inputJson 入参 JSON
   * @param resultJson 结果 JSON
   * @param success 是否执行成功
   * @param errorMessage 错误原因
   * @param durationMs 执行耗时
   * @param createdAt 创建时间
   */
  public ToolInvocationEntity(
      String id,
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs,
      Instant createdAt) {
    this.id = id != null ? id : UUID.randomUUID().toString();
    this.sessionId = sessionId != null ? sessionId : DEFAULT_SESSION;
    this.toolName = toolName != null ? toolName : DEFAULT_UNKNOWN;
    this.inputJson = inputJson;
    this.resultJson = resultJson;
    this.success = success;
    this.errorMessage = errorMessage;
    this.durationMs = durationMs;
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

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getInputJson() {
    return inputJson;
  }

  public void setInputJson(String inputJson) {
    this.inputJson = inputJson;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
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

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
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
    ToolInvocationEntity that = (ToolInvocationEntity) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ToolInvocationEntity{"
        + "id='"
        + id
        + '\''
        + ", sessionId='"
        + sessionId
        + '\''
        + ", toolName='"
        + toolName
        + '\''
        + ", success="
        + success
        + ", durationMs="
        + durationMs
        + '}';
  }
}
