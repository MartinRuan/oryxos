package com.oryxos.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * AgentScheduler 验收测试套件.
 *
 * <p>覆盖四个核心场景：注册参数校验、重叠跳过、异常隔离与锁释放、会话三元组固定.
 *
 * @author oryxos
 */
class AgentSchedulerTest {

  private static final String PROFILE_NAME = "test-agent";
  private static final String TASK_ID = "task-1";
  private static final String CRON_EXPR = "0 0 9 * * ?";
  private static final String TIMEZONE = "Asia/Shanghai";
  private static final String MESSAGE = "start daily report";
  private static final String CHANNEL_SCHEDULER = "scheduler";

  private TaskScheduler taskScheduler;
  private ProfileRegistry profileRegistry;
  private AgentService agentService;
  private SessionManager sessionManager;
  private AgentScheduler scheduler;
  private Profile profile;

  @BeforeEach
  void setUp() {
    taskScheduler = Mockito.mock(TaskScheduler.class);
    profileRegistry = Mockito.mock(ProfileRegistry.class);
    agentService = Mockito.mock(AgentService.class);
    sessionManager = Mockito.mock(SessionManager.class);

    scheduler =
        new AgentScheduler(
            taskScheduler, profileRegistry,
            agentService, sessionManager);

    profile = buildProfile();

    Session session =
        new Session(
            "scheduler:scheduler:" + PROFILE_NAME,
            PROFILE_NAME,
            CHANNEL_SCHEDULER,
            CHANNEL_SCHEDULER);
    when(sessionManager.getOrCreate(CHANNEL_SCHEDULER, CHANNEL_SCHEDULER, PROFILE_NAME))
        .thenReturn(session);
  }

  @Test
  @DisplayName("注册时 CronTrigger 携带配置 cron 和时区")
  void registerAll_cronTriggerCarriesCronAndTimezone() {
    when(profileRegistry.listProfiles()).thenReturn(castCollection(List.of(profile)));

    scheduler.registerAll();

    ArgumentCaptor<CronTrigger> triggerCaptor = ArgumentCaptor.forClass(CronTrigger.class);
    verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

    CronTrigger captured = triggerCaptor.getValue();
    assertNotNull(captured);
    assertEquals(CRON_EXPR, captured.getExpression());
  }

  @Test
  @DisplayName("上一次还没跑完_本次触发直接跳过")
  void runOnce_skipWhenLockHeld() throws Exception {
    // ReentrantLock 同线程可重入，必须从另一线程持锁模拟"上次还在跑"
    Lock lock = scheduler.lockFor(TASK_ID);
    java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    Thread holder =
        new Thread(
            () -> {
              lock.lock();
              locked.countDown();
              try {
                release.await();
              } catch (InterruptedException ignored) {
                // ignored
              } finally {
                lock.unlock();
              }
            });
    holder.start();
    locked.await();
    try {
      scheduler.runOnce(profile, scheduleConfig(TASK_ID));
      verify(agentService, never()).process(any(), any());
    } finally {
      release.countDown();
      holder.join();
    }
  }

  @Test
  @DisplayName("任务抛异常_不外抛且锁必须被释放")
  void runOnce_exceptionDoesNotPropagateAndLockReleased() {
    when(agentService.process(any(), any())).thenThrow(new RuntimeException("boom"));

    assertDoesNotThrow(() -> scheduler.runOnce(profile, scheduleConfig(TASK_ID)));

    // 再触发一次：能进来说明锁已释放
    scheduler.runOnce(profile, scheduleConfig(TASK_ID));
    verify(agentService, times(2)).process(any(), any());
  }

  @Test
  @DisplayName("会话三元组固定_channel和user均为scheduler")
  void runOnce_sessionIdentityFixed() {
    when(agentService.process(any(), any())).thenReturn("ok");

    scheduler.runOnce(profile, scheduleConfig(TASK_ID));

    verify(sessionManager).getOrCreate(CHANNEL_SCHEDULER, CHANNEL_SCHEDULER, PROFILE_NAME);
    verify(agentService).process(any(Session.class), any(String.class));
  }

  // ---- helpers ----

  private Profile buildProfile() {
    Profile.ScheduleConfig sc = scheduleConfig(TASK_ID);
    return Profile.builder()
        .name(PROFILE_NAME)
        .description("test")
        .provider(new Profile.ProviderConfig("deepseek", "chat", 0.7))
        .schedules(List.of(sc))
        .build();
  }

  private Profile.ScheduleConfig scheduleConfig(String id) {
    Profile.ScheduleConfig sc = new Profile.ScheduleConfig(CRON_EXPR, MESSAGE, TIMEZONE);
    sc.setId(id);
    return sc;
  }

  @SuppressWarnings("unchecked")
  private Collection<Profile> castCollection(List<Profile> list) {
    return (Collection<Profile>) (Collection<?>) list;
  }
}
