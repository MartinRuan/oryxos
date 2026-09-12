package com.oryxos.web.dto;

/**
 * Tool 元数据只读视图.
 *
 * @param name 工具名称
 * @param description 工具描述
 * @param inputSchema 输入 JSON Schema
 */
public record ToolSummary(String name, String description, String inputSchema) {}
