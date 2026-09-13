package com.oryxos.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * SQLite 中的定时任务运行状态.
 *
 * @author oryxos
 */
@Entity
@Table(
    name = "scheduled_tasks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_scheduled_tasks_profile_key",
            columnNames = {"profile_name", "schedule_key"}))
public class ScheduledTaskEntity {

  @Id
  @Column(name = "schedule_id", length = 64, nullable = false)
  private String scheduleId;

  @Column(name = "profile_name", length = 64, nullable = false)
  private String profileName;

  @Column(name = "schedule_key", length = 128, nullable = false)
  private String scheduleKey;

  @Column(name = "display_name", length = 256, nullable = false)
  private String displayName;

  @Column(name = "cron", length = 128, nullable = false)
  private String cron;

  @Column(name = "zone", length = 64, nullable = false)
  private String zone;

  @Column(name = "message", columnDefinition = "TEXT", nullable = false)
  private String message;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "retired", nullable = false)
  private boolean retired;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @Column(name = "last_status", length = 32)
  private String lastStatus;

  @Column(name = "run_count", nullable = false)
  private long runCount;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 创建默认启用的新任务实体. */
  public ScheduledTaskEntity() {
    scheduleId = UUID.randomUUID().toString();
    enabled = true;
    updatedAt = Instant.now();
  }

  public String getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(String scheduleId) {
    this.scheduleId = scheduleId;
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  public String getScheduleKey() {
    return scheduleKey;
  }

  public void setScheduleKey(String scheduleKey) {
    this.scheduleKey = scheduleKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isRetired() {
    return retired;
  }

  public void setRetired(boolean retired) {
    this.retired = retired;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(Instant nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(Instant lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public String getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(String lastStatus) {
    this.lastStatus = lastStatus;
  }

  public long getRunCount() {
    return runCount;
  }

  public void setRunCount(long runCount) {
    this.runCount = runCount;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
