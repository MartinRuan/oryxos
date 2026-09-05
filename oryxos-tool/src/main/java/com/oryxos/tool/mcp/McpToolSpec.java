package com.oryxos.tool.mcp;

import java.io.Serializable;

/**
 * 外部 MCP 服务暴露的工具规范描述.
 *
 * @param name 工具名称
 * @param description 工具描述说明
 * @param inputSchema 入参 JSON Schema 定义字符串
 * @author OryxOS Team
 */
public record McpToolSpec(String name, String description, String inputSchema)
    implements Serializable {}
