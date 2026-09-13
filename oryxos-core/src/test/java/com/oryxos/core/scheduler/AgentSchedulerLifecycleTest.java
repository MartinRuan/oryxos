package com.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class AgentSchedulerLifecycleTest {

  @Test
  void stoppingAndStartingTaskSynchronizesPersistentStateAndRuntimeHandle() {
    TaskScheduler taskScheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
    doReturn(firstFuture, secondFuture)
        .when(taskScheduler)
        .schedule(any(Runnable.class), any(Trigger.class));

    Profile profile = profile();
    ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
    when(profileRegistry.listProfiles()).thenReturn(List.of(profile));
    when(profileRegistry.getRequiredProfile("ops")).thenReturn(profile);
    SessionManager sessionManager = mock(SessionManager.class);
    when(sessionManager.getOrCreate("scheduler", "scheduler", "ops"))
        .thenReturn(new Session("scheduler:scheduler:ops", "ops", "scheduler", "scheduler"));
    AgentService agentService = mock(AgentService.class);
    ScheduledTaskStore store = ScheduledTaskStore.inMemory();
    AgentScheduler scheduler =
        new AgentScheduler(taskScheduler, profileRegistry, agentService, sessionManager, store);

    scheduler.registerAll();
    String scheduleId = scheduler.list().getFirst().scheduleId();

    assertThat(scheduler.setEnabled(scheduleId, false).enabled()).isFalse();
    assertThat(scheduler.list().getFirst().nextRunAt()).isNull();
    verify(firstFuture).cancel(false);

    assertThat(scheduler.setEnabled(scheduleId, true).enabled()).isTrue();
    assertThat(scheduler.list().getFirst().nextRunAt()).isNotNull();
    verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

    scheduler.runNow(scheduleId);
    assertThat(scheduler.executions(scheduleId)).hasSize(1);
    assertThat(scheduler.list().getFirst().runCount()).isEqualTo(1);
    verify(agentService).process(any(Session.class), any(String.class));
  }

  private Profile profile() {
    Profile.ScheduleConfig schedule =
        new Profile.ScheduleConfig("0 0 9 * * *", "send report", "Asia/Shanghai");
    schedule.setId("daily-report");
    Profile profile = new Profile();
    profile.setName("ops");
    profile.setSchedules(List.of(schedule));
    return profile;
  }
}
