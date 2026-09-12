package com.oryxos.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话详情与最近消息.
 *
 * @param id 会话标识
 * @param profileName Profile 名称
 * @param channel 渠道
 * @param userId 用户标识
 * @param status 状态
 * @param messageCount 消息总数
 * @param createdAt 创建时间
 * @param lastActiveAt 最后活动时间
 * @param archivedAt 归档时间
 * @param messages 最近消息
 */
public record SessionDetail(
    String id,
    String profileName,
    String channel,
    String userId,
    String status,
    int messageCount,
    LocalDateTime createdAt,
    LocalDateTime lastActiveAt,
    LocalDateTime archivedAt,
    List<MessageView> messages) {}
