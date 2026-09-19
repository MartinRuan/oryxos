package com.oryxos.core.model;

import java.util.List;

/**
 * 可安全返回给管理端的 Agent 定义视图，不包含任何凭证或通知配置值.
 *
 * @author oryxos
 */
public record AgentView(
    String name,
    String description,
    String instructions,
    ProviderView provider,
    List<String> tools,
    List<String> mcpServers,
    List<NotifyChannelView> notifyChannels,
    List<ScheduleView> schedules,
    String sourcePath,
    String agentMarkdown) {

  /** Provider 的非敏感字段. */
  public record ProviderView(String name, String model, Double temperature) {}

  /** 通知渠道的非敏感字段. */
  public record NotifyChannelView(String name, String type) {}

  /** 定时任务定义. */
  public record ScheduleView(String id, String cron, String message, String timezone) {}
}
