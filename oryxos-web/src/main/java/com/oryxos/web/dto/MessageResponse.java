package com.oryxos.web.dto;

/**
 * 有状态会话消息响应.
 *
 * @param sessionId 会话标识
 * @param reply Agent 回复
 */
public record MessageResponse(String sessionId, String reply) {}
