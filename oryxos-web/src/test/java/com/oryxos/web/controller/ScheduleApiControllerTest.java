package com.oryxos.web.controller;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskStore.ExecutionState;
import com.oryxos.core.scheduler.ScheduledTaskStore.TaskState;
import com.oryxos.web.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScheduleApiControllerTest {

  private AgentScheduler scheduler;
  private MockMvc mockMvc;
  private TaskState task;

  @BeforeEach
  void setUp() {
    scheduler = mock(AgentScheduler.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ScheduleApiController(scheduler))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    task =
        new TaskState(
            "stable-id",
            "minimax-agent",
            "daily-report",
            "Daily report",
            "0 0 9 * * *",
            "Asia/Shanghai",
            "confidential task message",
            true,
            false,
            Instant.parse("2026-09-13T01:00:00Z"),
            null,
            null,
            0,
            Instant.now());
  }

  @Test
  void listAndUpdateExposeSafeStateAndDelegateLifecycle() throws Exception {
    when(scheduler.list()).thenReturn(List.of(task));
    when(scheduler.setEnabled("stable-id", false))
        .thenReturn(
            new TaskState(
                task.scheduleId(),
                task.profileName(),
                task.scheduleKey(),
                task.displayName(),
                task.cron(),
                task.zone(),
                task.message(),
                false,
                false,
                null,
                null,
                null,
                0,
                Instant.now()));

    mockMvc
        .perform(get("/api/v2/schedules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].scheduleId").value("stable-id"))
        .andExpect(jsonPath("$.data[0].enabled").value(true))
        .andExpect(
            content()
                .string(not(org.hamcrest.Matchers.containsString("confidential task message"))));

    mockMvc
        .perform(
            put("/api/v2/schedules/stable-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
    verify(scheduler).setEnabled("stable-id", false);
  }

  @Test
  void runAndExecutionsDelegateToScheduler() throws Exception {
    when(scheduler.executions("stable-id"))
        .thenReturn(
            List.of(
                new ExecutionState(
                    1L,
                    "stable-id",
                    "scheduler:scheduler:minimax-agent",
                    Instant.parse("2026-09-12T01:00:00Z"),
                    true,
                    null,
                    25)));

    mockMvc.perform(post("/api/v2/schedules/stable-id/run")).andExpect(status().isOk());
    verify(scheduler).runNow("stable-id");

    mockMvc
        .perform(get("/api/v2/schedules/stable-id/executions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].sessionId").value("scheduler:scheduler:minimax-agent"))
        .andExpect(jsonPath("$.data[0].success").value(true));
  }
}
