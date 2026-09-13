package com.oryxos.web.controller;

import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.scheduler.ScheduledTaskStore.ExecutionState;
import com.oryxos.core.scheduler.ScheduledTaskStore.TaskState;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.ScheduledTaskView;
import com.oryxos.web.dto.TaskExecutionView;
import com.oryxos.web.dto.UpdateScheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务运行状态与操作 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v2")
@Tag(name = "Schedules", description = "定时任务运行管理")
public class ScheduleApiController {

  private final AgentScheduler agentScheduler;

  public ScheduleApiController(AgentScheduler agentScheduler) {
    this.agentScheduler = agentScheduler;
  }

  /** 列出所有活动任务. */
  @GetMapping("/schedules")
  @Operation(summary = "列出定时任务")
  public ApiResponse<List<ScheduledTaskView>> list() {
    return ApiResponse.success(agentScheduler.list().stream().map(this::toView).toList());
  }

  /** 查询指定任务的执行历史. */
  @GetMapping("/schedules/{scheduleId}/executions")
  @Operation(summary = "查询任务执行历史")
  public ApiResponse<List<TaskExecutionView>> executions(@PathVariable String scheduleId) {
    return ApiResponse.success(
        agentScheduler.executions(scheduleId).stream().map(this::toExecutionView).toList());
  }

  /** 立即执行一次任务. */
  @PostMapping("/schedules/{scheduleId}/run")
  @Operation(summary = "立即执行任务")
  public ApiResponse<Void> run(@PathVariable String scheduleId) {
    agentScheduler.runNow(scheduleId);
    return ApiResponse.success();
  }

  /** 使用 Agent 名称与配置 key 立即执行任务. */
  @PostMapping("/agents/{profileName}/schedules/{key}/run")
  @Operation(summary = "按 Agent 与配置 key 立即执行任务")
  public ApiResponse<Void> runByDefinition(
      @PathVariable String profileName, @PathVariable String key) {
    agentScheduler.runNow(profileName, key);
    return ApiResponse.success();
  }

  /** 启用或停用任务. */
  @PutMapping("/schedules/{scheduleId}")
  @Operation(summary = "启用或停用任务")
  public ApiResponse<ScheduledTaskView> update(
      @PathVariable String scheduleId, @Valid @RequestBody UpdateScheduleRequest request) {
    return ApiResponse.success(toView(agentScheduler.setEnabled(scheduleId, request.enabled())));
  }

  private ScheduledTaskView toView(TaskState task) {
    return new ScheduledTaskView(
        task.scheduleId(),
        task.profileName(),
        task.scheduleKey(),
        task.displayName(),
        task.cron(),
        task.zone(),
        task.enabled(),
        task.nextRunAt(),
        task.lastRunAt(),
        task.lastStatus(),
        task.runCount());
  }

  private TaskExecutionView toExecutionView(ExecutionState execution) {
    return new TaskExecutionView(
        execution.id(),
        execution.scheduleId(),
        execution.sessionId(),
        execution.startedAt(),
        execution.success(),
        execution.errorMessage(),
        execution.durationMs());
  }
}
