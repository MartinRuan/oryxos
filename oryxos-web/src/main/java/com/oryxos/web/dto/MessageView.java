package com.oryxos.web.dto;

/**
 * 安全的会话消息只读视图.
 *
 * @param role 消息角色
 * @param content 消息正文
 * @param toolCallId 工具调用关联标识
 */
public record MessageView(String role, String content, String toolCallId) {}
