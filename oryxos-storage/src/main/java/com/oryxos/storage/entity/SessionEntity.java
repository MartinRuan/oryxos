package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 会话持久化实体. 映射 SQLite 的 sessions 表.
 *
 * @author OryxOS Team
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

  @Id
  @Column(name = "session_id", length = 128, nullable = false)
  private String sessionId;

  @Column(name = "profile_name", length = 64, nullable = false)
  private String profileName;

  @Column(name = "channel", length = 32, nullable = false)
  private String channel;

  @Column(name = "user_id", length = 64)
  private String userId;

  @Lob
  @Column(name = "messages_json", columnDefinition = "TEXT")
  private String messagesJson;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_active_at", nullable = false)
  private LocalDateTime lastActiveAt;

  @Column(name = "archived_at")
  private LocalDateTime archivedAt;

  /** 默认无参构造器. */
  public SessionEntity() {
    this.status = "ACTIVE";
    this.messagesJson = "[]";
    this.createdAt = LocalDateTime.now();
    this.lastActiveAt = LocalDateTime.now();
  }

  /**
   * 基础全参构造器.
   *
   * @param sessionId 会话主键 ID
   * @param profileName 关联的 Profile 名称
   * @param channel 渠道名称
   * @param userId 用户标识
   * @param messagesJson 消息序列化 JSON
   * @param status 状态 (ACTIVE / ARCHIVED)
   * @param createdAt 创建时间
   * @param lastActiveAt 最后活跃时间
   * @param archivedAt 归档时间
   */
  public SessionEntity(
      String sessionId,
      String profileName,
      String channel,
      String userId,
      String messagesJson,
      String status,
      LocalDateTime createdAt,
      LocalDateTime lastActiveAt,
      LocalDateTime archivedAt) {
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.channel = channel != null ? channel : "cli";
    this.userId = userId;
    this.messagesJson = messagesJson != null ? messagesJson : "[]";
    this.status = status != null ? status : "ACTIVE";
    this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    this.lastActiveAt = lastActiveAt != null ? lastActiveAt : LocalDateTime.now();
    this.archivedAt = archivedAt;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
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

  public String getMessagesJson() {
    return messagesJson;
  }

  public void setMessagesJson(String messagesJson) {
    this.messagesJson = messagesJson;
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
    SessionEntity that = (SessionEntity) o;
    return Objects.equals(sessionId, that.sessionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sessionId);
  }
}
