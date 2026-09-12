package com.oryxos.web.dto;

import java.time.LocalDateTime;

/**
 * 不含消息正文的会话摘要.
 *
 * @param id 会话标识
 * @param profileName Profile 名称
 * @param channel 渠道
 * @param userId 用户标识
 * @param status 状态
 * @param messageCount 消息数量
 * @param createdAt 创建时间
 * @param lastActiveAt 最后活动时间
 * @param archivedAt 归档时间
 */
public record SessionSummary(
    String id,
    String profileName,
    String channel,
    String userId,
    String status,
    int messageCount,
    LocalDateTime createdAt,
    LocalDateTime lastActiveAt,
    LocalDateTime archivedAt) {}
