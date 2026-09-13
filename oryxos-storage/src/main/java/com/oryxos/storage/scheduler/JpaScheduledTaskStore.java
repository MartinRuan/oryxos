package com.oryxos.storage.scheduler;

import com.oryxos.core.scheduler.ScheduledTaskStore;
import com.oryxos.storage.entity.ScheduledTaskEntity;
import com.oryxos.storage.entity.TaskExecutionEntity;
import com.oryxos.storage.repository.ScheduledTaskRepository;
import com.oryxos.storage.repository.TaskExecutionRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA 与 SQLite 的定时任务状态存储.
 *
 * @author oryxos
 */
@Primary
@Component
public class JpaScheduledTaskStore implements ScheduledTaskStore {

  private final ScheduledTaskRepository taskRepository;
  private final TaskExecutionRepository executionRepository;

  public JpaScheduledTaskStore(
      ScheduledTaskRepository taskRepository, TaskExecutionRepository executionRepository) {
    this.taskRepository = taskRepository;
    this.executionRepository = executionRepository;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public TaskState reconcile(
      String profileName,
      String scheduleKey,
      String displayName,
      String cron,
      String zone,
      String message) {
    ScheduledTaskEntity entity =
        taskRepository
            .findByProfileNameAndScheduleKey(profileName, scheduleKey)
            .orElseGet(ScheduledTaskEntity::new);
    entity.setProfileName(profileName);
    entity.setScheduleKey(scheduleKey);
    entity.setDisplayName(displayName);
    entity.setCron(cron);
    entity.setZone(zone);
    entity.setMessage(message);
    entity.setRetired(false);
    entity.setUpdatedAt(Instant.now());
    return toState(taskRepository.save(entity));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void retire(String profileName, Collection<String> activeKeys) {
    List<ScheduledTaskEntity> tasks = taskRepository.findByProfileName(profileName);
    for (ScheduledTaskEntity task : tasks) {
      if (!activeKeys.contains(task.getScheduleKey()) && !task.isRetired()) {
        task.setRetired(true);
        task.setNextRunAt(null);
        task.setUpdatedAt(Instant.now());
      }
    }
    taskRepository.saveAll(tasks);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TaskState> find(String scheduleId) {
    return taskRepository.findById(scheduleId).filter(task -> !task.isRetired()).map(this::toState);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isEnabled(String scheduleId) {
    return taskRepository
        .findById(scheduleId)
        .filter(task -> !task.isRetired())
        .map(ScheduledTaskEntity::isEnabled)
        .orElse(false);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Optional<TaskState> setEnabled(String scheduleId, boolean enabled) {
    return taskRepository
        .findById(scheduleId)
        .filter(task -> !task.isRetired())
        .map(
            task -> {
              task.setEnabled(enabled);
              task.setUpdatedAt(Instant.now());
              return toState(taskRepository.save(task));
            });
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updateNextRunAt(String scheduleId, Instant nextRunAt) {
    taskRepository
        .findById(scheduleId)
        .ifPresent(
            task -> {
              task.setNextRunAt(nextRunAt);
              task.setUpdatedAt(Instant.now());
              taskRepository.save(task);
            });
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void recordExecution(
      String scheduleId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs) {
    Optional<ScheduledTaskEntity> found = taskRepository.findById(scheduleId);
    if (found.isEmpty()) {
      return;
    }
    TaskExecutionEntity execution = new TaskExecutionEntity();
    execution.setScheduleId(scheduleId);
    execution.setSessionId(sessionId);
    execution.setStartedAt(startedAt);
    execution.setSuccess(success);
    execution.setErrorMessage(errorMessage);
    execution.setDurationMs(durationMs);
    executionRepository.save(execution);

    ScheduledTaskEntity task = found.get();
    task.setLastRunAt(startedAt);
    task.setLastStatus(success ? "success" : "failed");
    task.setRunCount(task.getRunCount() + 1);
    task.setUpdatedAt(Instant.now());
    taskRepository.save(task);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TaskState> list() {
    return taskRepository.findAllByRetiredFalseOrderByProfileNameAscScheduleKeyAsc().stream()
        .map(this::toState)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ExecutionState> executions(String scheduleId) {
    return executionRepository.findByScheduleIdOrderByStartedAtDesc(scheduleId).stream()
        .map(this::toExecution)
        .toList();
  }

  private TaskState toState(ScheduledTaskEntity task) {
    return new TaskState(
        task.getScheduleId(),
        task.getProfileName(),
        task.getScheduleKey(),
        task.getDisplayName(),
        task.getCron(),
        task.getZone(),
        task.getMessage(),
        task.isEnabled(),
        task.isRetired(),
        task.getNextRunAt(),
        task.getLastRunAt(),
        task.getLastStatus(),
        task.getRunCount(),
        task.getUpdatedAt());
  }

  private ExecutionState toExecution(TaskExecutionEntity execution) {
    return new ExecutionState(
        execution.getId(),
        execution.getScheduleId(),
        execution.getSessionId(),
        execution.getStartedAt(),
        execution.isSuccess(),
        execution.getErrorMessage(),
        execution.getDurationMs());
  }
}
