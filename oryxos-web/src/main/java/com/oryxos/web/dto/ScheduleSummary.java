package com.oryxos.web.dto;

/**
 * Safe schedule metadata without trigger messages or notification targets.
 *
 * @param id schedule identifier
 * @param cron cron expression
 * @param timezone time zone
 */
public record ScheduleSummary(String id, String cron, String timezone) {}
