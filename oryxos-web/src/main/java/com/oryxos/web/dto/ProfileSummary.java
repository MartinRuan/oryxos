package com.oryxos.web.dto;

import java.util.List;

/**
 * 不含凭证与通知目标的 Profile 摘要.
 *
 * @param name Profile 名称
 * @param description 描述
 * @param provider Provider 名称
 * @param model 模型名称
 * @param tools 可用工具名称
 * @param schedules 定时任务安全摘要
 */
public record ProfileSummary(
    String name,
    String description,
    String provider,
    String model,
    List<String> tools,
    List<ScheduleSummary> schedules) {}
