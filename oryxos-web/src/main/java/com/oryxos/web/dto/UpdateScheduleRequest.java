package com.oryxos.web.dto;

import jakarta.validation.constraints.NotNull;

/** 定时任务启停请求. */
public record UpdateScheduleRequest(@NotNull Boolean enabled) {}
