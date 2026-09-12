package com.oryxos.web.dto;

/**
 * 一次性 Agent 调用响应，仅暴露最终回复.
 *
 * @param reply Agent 回复
 */
public record AgentReply(String reply) {}
