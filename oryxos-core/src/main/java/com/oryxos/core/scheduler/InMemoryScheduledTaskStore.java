package com.oryxos.core.scheduler;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 无持久化模块时使用的进程内任务状态存储. */
final class InMemoryScheduledTaskStore implements ScheduledTaskStore {

  private final Map<String, MutableTask> tasks = new ConcurrentHashMap<>();
  private final Map<String, List<ExecutionState>> histories = new ConcurrentHashMap<>();

  @Override
  public synchronized TaskState reconcile(
      String profileName,
      String scheduleKey,
      String displayName,
      String cron,
      String zone,
      String message) {
    MutableTask task =
        tasks.values().stream()
            .filter(
                item ->
                    item.profileName.equals(profileName) && item.scheduleKey.equals(scheduleKey))
            .findFirst()
            .orElseGet(
                () -> {
                  MutableTask created =
                      new MutableTask(UUID.randomUUID().toString(), profileName, scheduleKey);
                  tasks.put(created.scheduleId, created);
                  return created;
                });
    task.displayName = displayName;
    task.cron = cron;
    task.zone = zone;
    task.message = message;
    task.retired = false;
    task.updatedAt = Instant.now();
    return task.snapshot();
  }

  @Override
  public synchronized void retire(String profileName, Collection<String> activeKeys) {
    tasks.values().stream()
        .filter(task -> task.profileName.equals(profileName))
        .filter(task -> !activeKeys.contains(task.scheduleKey))
        .forEach(
            task -> {
              task.retired = true;
              task.nextRunAt = null;
              task.updatedAt = Instant.now();
            });
  }

  @Override
  public Optional<TaskState> find(String scheduleId) {
    MutableTask task = tasks.get(scheduleId);
    return task == null || task.retired ? Optional.empty() : Optional.of(task.snapshot());
  }

  @Override
  public boolean isEnabled(String scheduleId) {
    return find(scheduleId).map(TaskState::enabled).orElse(false);
  }

  @Override
  public synchronized Optional<TaskState> setEnabled(String scheduleId, boolean enabled) {
    MutableTask task = tasks.get(scheduleId);
    if (task == null || task.retired) {
      return Optional.empty();
    }
    task.enabled = enabled;
    task.updatedAt = Instant.now();
    return Optional.of(task.snapshot());
  }

  @Override
  public synchronized void updateNextRunAt(String scheduleId, Instant nextRunAt) {
    MutableTask task = tasks.get(scheduleId);
    if (task != null) {
      task.nextRunAt = nextRunAt;
      task.updatedAt = Instant.now();
    }
  }

  @Override
  public synchronized void recordExecution(
      String scheduleId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs) {
    MutableTask task = tasks.get(scheduleId);
    if (task == null) {
      return;
    }
    List<ExecutionState> values =
        new java.util.ArrayList<>(histories.getOrDefault(scheduleId, List.of()));
    values.add(
        0,
        new ExecutionState(
            (long) values.size() + 1,
            scheduleId,
            sessionId,
            startedAt,
            success,
            errorMessage,
            durationMs));
    histories.put(scheduleId, List.copyOf(values));
    task.lastRunAt = startedAt;
    task.lastStatus = success ? "success" : "failed";
    task.runCount++;
    task.updatedAt = Instant.now();
  }

  @Override
  public List<TaskState> list() {
    return tasks.values().stream()
        .filter(task -> !task.retired)
        .map(MutableTask::snapshot)
        .sorted(
            java.util.Comparator.comparing(TaskState::profileName)
                .thenComparing(TaskState::scheduleKey))
        .toList();
  }

  @Override
  public List<ExecutionState> executions(String scheduleId) {
    return histories.getOrDefault(scheduleId, List.of());
  }

  private static final class MutableTask {
    private final String scheduleId;
    private final String profileName;
    private final String scheduleKey;
    private String displayName;
    private String cron;
    private String zone;
    private String message;
    private boolean enabled = true;
    private boolean retired;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private String lastStatus;
    private long runCount;
    private Instant updatedAt = Instant.now();

    private MutableTask(String scheduleId, String profileName, String scheduleKey) {
      this.scheduleId = scheduleId;
      this.profileName = profileName;
      this.scheduleKey = scheduleKey;
    }

    private TaskState snapshot() {
      return new TaskState(
          scheduleId,
          profileName,
          scheduleKey,
          displayName,
          cron,
          zone,
          message,
          enabled,
          retired,
          nextRunAt,
          lastRunAt,
          lastStatus,
          runCount,
          updatedAt);
    }
  }
}
