package com.oryxos.core.scheduler;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Profile.ScheduleConfig;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.scheduler.ScheduledTaskStore.ExecutionState;
import com.oryxos.core.scheduler.ScheduledTaskStore.TaskState;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 定时任务调度器. 将 Agent 配置定义协调为稳定任务状态，并统一调用 {@link AgentService}.
 *
 * <p>核心阶段单实例部署，使用进程内 {@link ReentrantLock} 防重叠执行.
 *
 * @author oryxos
 */
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);
  private static final String CHANNEL_SCHEDULER = "scheduler";

  private final TaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final SessionManager sessionManager;
  private final ScheduledTaskStore taskStore;
  private final ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, TaskBinding> taskBindings = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks =
      new ConcurrentHashMap<>();

  /**
   * 保留的基础构造器，使用进程内状态存储.
   *
   * @param taskScheduler Spring 任务调度器
   * @param profileRegistry Profile 注册中心
   * @param agentService Agent 统一执行门面
   * @param sessionManager 会话管理器
   */
  public AgentScheduler(
      TaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager) {
    this(
        taskScheduler,
        profileRegistry,
        agentService,
        sessionManager,
        ScheduledTaskStore.inMemory());
  }

  /**
   * 创建带可持久化任务状态的调度器.
   *
   * @param taskScheduler Spring 任务调度器
   * @param profileRegistry Profile 注册中心
   * @param agentService Agent 统一执行门面
   * @param sessionManager 会话管理器
   * @param taskStore 任务状态与历史存储
   */
  public AgentScheduler(
      TaskScheduler taskScheduler,
      ProfileRegistry profileRegistry,
      AgentService agentService,
      SessionManager sessionManager,
      ScheduledTaskStore taskStore) {
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
    this.taskStore = taskStore;
  }

  /** 协调并注册所有已加载 Agent 的定时任务. */
  public synchronized void registerAll() {
    cancelAll();
    taskBindings.clear();
    Collection<Profile> profiles = profileRegistry.listProfiles();
    Set<String> activeProfiles = new HashSet<>();
    for (Profile profile : profiles) {
      activeProfiles.add(profile.getName());
      registerProfile(profile);
    }
    taskStore.list().stream()
        .filter(task -> !activeProfiles.contains(task.profileName()))
        .forEach(
            task -> {
              cancel(task.scheduleId());
              taskBindings.remove(task.scheduleId());
            });
    taskStore.list().stream()
        .map(TaskState::profileName)
        .filter(profileName -> !activeProfiles.contains(profileName))
        .distinct()
        .forEach(profileName -> taskStore.retire(profileName, Set.of()));
  }

  /**
   * 协调并注册单个 Agent 的全部定时任务.
   *
   * @param profile Agent 运行配置
   */
  public synchronized void registerProfile(Profile profile) {
    java.util.Objects.requireNonNull(profile, "profile");
    java.util.Objects.requireNonNull(profile.getName(), "profile.name");

    Set<String> activeKeys = new HashSet<>();
    Set<String> previousScheduleIds =
        taskStore.list().stream()
            .filter(task -> profile.getName().equals(task.profileName()))
            .map(TaskState::scheduleId)
            .collect(java.util.stream.Collectors.toSet());

    for (ScheduleConfig schedule : profile.getSchedules()) {
      String key = schedule.getId();
      activeKeys.add(key);
      TaskState state =
          taskStore.reconcile(
              profile.getName(),
              key,
              key,
              schedule.getCron(),
              schedule.getZoneId().getId(),
              schedule.getMessage());
      previousScheduleIds.remove(state.scheduleId());
      cancel(state.scheduleId());
      taskBindings.put(state.scheduleId(), new TaskBinding(profile, schedule));
      if (state.enabled()) {
        schedule(state.scheduleId(), profile, schedule);
      }
    }

    previousScheduleIds.forEach(
        scheduleId -> {
          cancel(scheduleId);
          taskBindings.remove(scheduleId);
        });
    taskStore.retire(profile.getName(), activeKeys);
  }

  boolean hasScheduledTask(String scheduleId) {
    return scheduledTasks.containsKey(scheduleId);
  }

  /** 列出活动任务状态. */
  public List<TaskState> list() {
    return taskStore.list();
  }

  /** 查询任务执行历史. */
  public List<ExecutionState> executions(String scheduleId) {
    requireTask(scheduleId);
    return taskStore.executions(scheduleId);
  }

  /** 启用或停用任务，并立即同步运行时调度句柄. */
  public synchronized TaskState setEnabled(String scheduleId, boolean enabled) {
    TaskState state = requireTask(scheduleId);
    taskStore.setEnabled(scheduleId, enabled).orElseThrow(() -> taskNotFound(scheduleId));
    if (enabled) {
      TaskBinding binding = requireBinding(state);
      if (!scheduledTasks.containsKey(scheduleId)) {
        schedule(scheduleId, binding.profile(), binding.schedule());
      }
    } else {
      cancel(scheduleId);
      taskStore.updateNextRunAt(scheduleId, null);
    }
    return requireTask(scheduleId);
  }

  /** 立即同步执行一次指定任务，不改变启停状态. */
  public void runNow(String scheduleId) {
    TaskState state = requireTask(scheduleId);
    TaskBinding binding = requireBinding(state);
    execute(scheduleId, binding.profile(), binding.schedule(), false);
  }

  /** 按 Agent 名称与配置 key 精确执行一次任务. */
  public void runNow(String profileName, String scheduleKey) {
    TaskState state =
        taskStore.list().stream()
            .filter(task -> task.profileName().equals(profileName))
            .filter(task -> task.scheduleKey().equals(scheduleKey))
            .findFirst()
            .orElseThrow(
                () ->
                    new OryxException(
                        StandardErrorCode.NOT_FOUND,
                        "Schedule not found: " + profileName + "/" + scheduleKey));
    runNow(state.scheduleId());
  }

  /**
   * 执行一次配置任务. 保留该入口供既有调用方与测试使用.
   *
   * @param profile 关联 Agent
   * @param schedule 调度配置
   */
  public void runOnce(Profile profile, ScheduleConfig schedule) {
    execute(schedule.getId(), profile, schedule, false);
  }

  /** 获取指定任务 ID 对应的锁实例. */
  public Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }

  private void schedule(String scheduleId, Profile profile, ScheduleConfig config) {
    CronTrigger trigger = new CronTrigger(config.getCron(), config.getZoneId());
    ScheduledFuture<?> future =
        taskScheduler.schedule(() -> execute(scheduleId, profile, config, true), trigger);
    if (future != null) {
      scheduledTasks.put(scheduleId, future);
    }
    updateNextRun(scheduleId, config);
    log.info(
        "Registered scheduled task [{}] for profile [{}] with cron [{}] timezone [{}]",
        scheduleId,
        profile.getName(),
        config.getCron(),
        config.getZoneId());
  }

  private void execute(
      String scheduleId, Profile profile, ScheduleConfig config, boolean requireEnabled) {
    if (requireEnabled && !taskStore.isEnabled(scheduleId)) {
      return;
    }
    Lock lock = lockFor(scheduleId);
    if (!lock.tryLock()) {
      log.info("Task [{}] still running, skip this trigger", scheduleId);
      return;
    }

    Instant startedAt = Instant.now();
    long startedNanos = System.nanoTime();
    String sessionId =
        SessionManager.generateSessionId(CHANNEL_SCHEDULER, CHANNEL_SCHEDULER, profile.getName());
    boolean success = false;
    String errorMessage = null;
    try {
      Session session =
          sessionManager.getOrCreate(CHANNEL_SCHEDULER, CHANNEL_SCHEDULER, profile.getName());
      sessionId = session.getId();
      agentService.process(session, config.getMessage());
      success = true;
    } catch (Exception e) {
      errorMessage = e.getMessage();
      log.error("Scheduled task [{}] failed: {}", scheduleId, e.getMessage(), e);
    } finally {
      long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
      taskStore.recordExecution(
          scheduleId, sessionId, startedAt, success, errorMessage, durationMs);
      if (scheduledTasks.containsKey(scheduleId)) {
        updateNextRun(scheduleId, config);
      }
      lock.unlock();
    }
  }

  private TaskBinding requireBinding(TaskState state) {
    TaskBinding binding = taskBindings.get(state.scheduleId());
    if (binding != null) {
      return binding;
    }
    Profile profile = profileRegistry.getRequiredProfile(state.profileName());
    ScheduleConfig schedule =
        profile.getSchedules().stream()
            .filter(item -> item.getId().equals(state.scheduleKey()))
            .findFirst()
            .orElseThrow(() -> taskNotFound(state.scheduleId()));
    TaskBinding resolved = new TaskBinding(profile, schedule);
    taskBindings.put(state.scheduleId(), resolved);
    return resolved;
  }

  private TaskState requireTask(String scheduleId) {
    return taskStore.find(scheduleId).orElseThrow(() -> taskNotFound(scheduleId));
  }

  private OryxException taskNotFound(String scheduleId) {
    return new OryxException(StandardErrorCode.NOT_FOUND, "Schedule not found: " + scheduleId);
  }

  private void updateNextRun(String scheduleId, ScheduleConfig config) {
    ZonedDateTime next =
        CronExpression.parse(config.getCron()).next(ZonedDateTime.now(config.getZoneId()));
    taskStore.updateNextRunAt(scheduleId, next != null ? next.toInstant() : null);
  }

  private void cancel(String scheduleId) {
    ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
    if (future != null) {
      future.cancel(false);
    }
  }

  private void cancelAll() {
    scheduledTasks.keySet().forEach(this::cancel);
  }

  private record TaskBinding(Profile profile, ScheduleConfig schedule) {}
}
