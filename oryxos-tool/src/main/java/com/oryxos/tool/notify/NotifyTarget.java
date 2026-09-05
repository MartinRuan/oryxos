package com.oryxos.tool.notify;

import java.util.Map;

/**
 * 出站通知目标值对象.
 *
 * @param channelType 渠道类型（如 "webhook"）
 * @param config 渠道配置字典（包含 url 等信息）
 * @author OryxOS Team
 */
public record NotifyTarget(String channelType, Map<String, String> config) {}
