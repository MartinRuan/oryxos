package com.oryxos.web.dto;

import java.time.Instant;

/** 定时任务单次执行历史视图. */
public record TaskExecutionView(
    Long id,
    String scheduleId,
    String sessionId,
    Instant startedAt,
    boolean success,
    String errorMessage,
    long durationMs) {}
