package com.oryxos.web.dto;

import java.time.Instant;

/** 不包含任务消息与凭证的定时任务管理视图. */
public record ScheduledTaskView(
    String scheduleId,
    String profileName,
    String scheduleKey,
    String displayName,
    String cron,
    String zone,
    boolean enabled,
    Instant nextRunAt,
    Instant lastRunAt,
    String lastStatus,
    long runCount) {}
