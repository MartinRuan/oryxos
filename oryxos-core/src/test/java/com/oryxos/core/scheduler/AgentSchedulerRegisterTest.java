package com.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oryxos.core.model.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

class AgentSchedulerRegisterTest {

  @Test
  void registerProfile保存句柄_复用cron时区_重复注册替换旧句柄() {
    TaskScheduler taskScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> first = mock(ScheduledFuture.class);
    ScheduledFuture<?> second = mock(ScheduledFuture.class);
    doReturn(first, second).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

    AgentScheduler scheduler =
        new AgentScheduler(
            taskScheduler,
            mock(ProfileRegistry.class),
            mock(AgentService.class),
            mock(SessionManager.class),
            ScheduledTaskStore.inMemory());
    Profile profile = profile();

    scheduler.registerProfile(profile);
    ScheduledTaskStore.TaskState state = scheduler.list().getFirst();

    assertThat(state.scheduleKey()).isEqualTo("reconcile-morning");
    assertThat(state.cron()).isEqualTo("0 0 9 * * *");
    assertThat(state.zone()).isEqualTo("Asia/Shanghai");
    assertThat(scheduler.hasScheduledTask(state.scheduleId())).isTrue();

    scheduler.registerProfile(profile);

    assertThat(scheduler.list()).hasSize(1);
    assertThat(scheduler.hasScheduledTask(state.scheduleId())).isTrue();
    verify(first).cancel(false);
    verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
    verify(taskScheduler, times(2)).schedule(any(Runnable.class), triggerCaptor.capture());
    assertThat(triggerCaptor.getAllValues())
        .allSatisfy(
            trigger -> {
              assertThat(trigger).isInstanceOf(CronTrigger.class);
              assertThat(((CronTrigger) trigger).getExpression()).isEqualTo("0 0 9 * * *");
            });
  }

  private Profile profile() {
    Profile.ScheduleConfig schedule =
        new Profile.ScheduleConfig("0 0 9 * * *", "执行每日对账", "Asia/Shanghai");
    schedule.setId("reconcile-morning");
    return Profile.builder()
        .name("daily-reconcile")
        .provider(new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.2))
        .schedules(List.of(schedule))
        .build();
  }
}
