package com.oryxos.core.scheduler;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Profile.ScheduleConfig;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 定时任务调度器. 作为第三种触发源（"钟推"），以配置驱动的方式动态注册 Profile 中声明的 {@code schedules} 规则，到点拼消息交给 {@link
 * AgentService} 走标准 ReAct 循环.
 *
 * <p>核心阶段单实例部署，使用进程内 {@link ReentrantLock} 防重叠执行.
 *
 * @author oryxos
 */
public class AgentScheduler {

  private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

  /** 固定渠道与用户标识，标记来源为调度器触发. */
  private static final String CHANNEL_SCHEDULER = "scheduler";

  private final TaskScheduler taskScheduler;
  private final ProfileRegistry profileRegistry;
  private final AgentService agentService;
  private final SessionManager sessionManager;

  /** 按任务 ID 隔离的本地锁表，防止同一任务重叠执行. */
  private final ConcurrentMap<String, Lock> taskLocks = new ConcurrentHashMap<>();

  /**
   * 构造 AgentScheduler.
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
    this.taskScheduler = taskScheduler;
    this.profileRegistry = profileRegistry;
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  /**
   * 注册所有已加载 Profile 中声明的定时任务. 遍历 Profile 列表，将每条 {@link ScheduleConfig} 以 {@link CronTrigger}
   * 方式动态注册到 {@link TaskScheduler}.
   */
  public void registerAll() {
    for (Profile profile : profileRegistry.listProfiles()) {
      for (ScheduleConfig sc : profile.getSchedules()) {
        CronTrigger trigger = new CronTrigger(sc.getCron(), sc.getZoneId());
        taskScheduler.schedule(() -> runOnce(profile, sc), trigger);
        log.info(
            "Registered scheduled task [{}] for profile" + " [{}] with cron [{}] timezone [{}]",
            sc.getId(),
            profile.getName(),
            sc.getCron(),
            sc.getZoneId());
      }
    }
  }

  /**
   * 执行一次定时任务. 使用 tryLock 防止重叠执行，失败时仅记录日志不外抛.
   *
   * @param profile 关联的 Agent Profile
   * @param sc 调度配置
   */
  public void runOnce(Profile profile, ScheduleConfig sc) {
    Lock lock = lockFor(sc.getId());
    if (!lock.tryLock()) {
      log.info("Task [{}] still running, skip this trigger", sc.getId());
      return;
    }
    try {
      Session session =
          sessionManager.getOrCreate(CHANNEL_SCHEDULER, CHANNEL_SCHEDULER, profile.getName());
      agentService.process(session, sc.getMessage());
    } catch (Exception e) {
      log.error("Scheduled task [{}] failed: {}", sc.getId(), e.getMessage(), e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * 获取指定任务 ID 对应的锁实例. 主要供测试验证使用.
   *
   * @param taskId 任务标识
   * @return 对应的 Lock 实例
   */
  public Lock lockFor(String taskId) {
    return taskLocks.computeIfAbsent(taskId, id -> new ReentrantLock());
  }
}
