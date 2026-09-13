package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * SQLite 中的一次定时任务执行历史.
 *
 * @author oryxos
 */
@Entity
@Table(name = "task_executions")
public class TaskExecutionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "schedule_id", length = 64)
  private String scheduleId;

  @Column(name = "session_id", length = 128, nullable = false)
  private String sessionId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message", length = 2048)
  private String errorMessage;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  /** JPA 构造器. */
  public TaskExecutionEntity() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(String scheduleId) {
    this.scheduleId = scheduleId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
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
}
