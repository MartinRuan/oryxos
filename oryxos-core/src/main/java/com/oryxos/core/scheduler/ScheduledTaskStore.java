package com.oryxos.core.scheduler;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 定时任务状态与执行历史存储契约.
 *
 * <p>任务定义仍来自 Agent 配置，存储层只保存稳定 ID、启停状态和执行结果.
 *
 * @author oryxos
 */
public interface ScheduledTaskStore {

  /**
   * 创建无外部持久化依赖的进程内实现.
   *
   * @return 进程内任务状态存储
   */
  static ScheduledTaskStore inMemory() {
    return new InMemoryScheduledTaskStore();
  }

  /**
   * 协调一条 Agent 配置定义，并复用既有稳定 ID 与启停状态.
   *
   * @param profileName Agent 名称
   * @param scheduleKey Agent 内任务配置键
   * @param displayName 展示名称
   * @param cron Cron 表达式
   * @param zone 时区
   * @param message 触发消息
   * @return 协调后的任务状态
   */
  TaskState reconcile(
      String profileName,
      String scheduleKey,
      String displayName,
      String cron,
      String zone,
      String message);

  /**
   * 将指定 Agent 中已经移除的配置任务标为退役.
   *
   * @param profileName Agent 名称
   * @param activeKeys 当前仍存在的配置键
   */
  void retire(String profileName, Collection<String> activeKeys);

  /**
   * 按稳定 ID 查询活动任务.
   *
   * @param scheduleId 稳定任务 ID
   * @return 活动任务
   */
  Optional<TaskState> find(String scheduleId);

  /**
   * 查询任务是否启用.
   *
   * @param scheduleId 稳定任务 ID
   * @return 是否启用
   */
  boolean isEnabled(String scheduleId);

  /**
   * 修改任务启停状态.
   *
   * @param scheduleId 稳定任务 ID
   * @param enabled 目标启停状态
   * @return 修改后的活动任务
   */
  Optional<TaskState> setEnabled(String scheduleId, boolean enabled);

  /**
   * 更新下一次计划执行时间.
   *
   * @param scheduleId 稳定任务 ID
   * @param nextRunAt 下次执行时间，停止时为空
   */
  void updateNextRunAt(String scheduleId, Instant nextRunAt);

  /**
   * 记录一次执行，并同步任务累计状态.
   *
   * @param scheduleId 稳定任务 ID
   * @param sessionId 调度会话 ID
   * @param startedAt 开始时间
   * @param success 是否成功
   * @param errorMessage 失败原因
   * @param durationMs 执行耗时
   */
  void recordExecution(
      String scheduleId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs);

  /**
   * 列出全部活动任务.
   *
   * @return 活动任务列表
   */
  List<TaskState> list();

  /**
   * 查询一条任务的执行历史.
   *
   * @param scheduleId 稳定任务 ID
   * @return 倒序执行历史
   */
  List<ExecutionState> executions(String scheduleId);

  /** 定时任务运行状态快照. */
  record TaskState(
      String scheduleId,
      String profileName,
      String scheduleKey,
      String displayName,
      String cron,
      String zone,
      String message,
      boolean enabled,
      boolean retired,
      Instant nextRunAt,
      Instant lastRunAt,
      String lastStatus,
      long runCount,
      Instant updatedAt) {}

  /** 单次任务执行快照. */
  record ExecutionState(
      Long id,
      String scheduleId,
      String sessionId,
      Instant startedAt,
      boolean success,
      String errorMessage,
      long durationMs) {}
}
