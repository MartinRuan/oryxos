package com.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;

/**
 * MCP 工具适配器.
 *
 * <p>将外部 MCP 服务暴露的工具规范包装适配为 OryxTool 统一抽象，调用时通过 JSON-RPC 转发.
 *
 * @author OryxOS Team
 */
public class McpToolAdapter implements OryxTool {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final McpClient client;
  private final McpToolSpec spec;

  /**
   * 构造 MCP 工具适配器.
   *
   * @param client MCP 客户端实例
   * @param spec 工具元数据规格
   */
  public McpToolAdapter(McpClient client, McpToolSpec spec) {
    this.client = client;
    this.spec = spec;
  }

  @Override
  public String getName() {
    return spec.name();
  }

  @Override
  public String getDescription() {
    return spec.description();
  }

  @Override
  public String getInputSchema() {
    return spec.inputSchema();
  }

  @Override
  public ToolResult execute(String inputJson) {
    JsonNode node;
    try {
      node =
          (inputJson != null && !inputJson.isBlank())
              ? OBJECT_MAPPER.readTree(inputJson)
              : OBJECT_MAPPER.createObjectNode();
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      node = OBJECT_MAPPER.createObjectNode();
    }
    return execute(node);
  }

  /**
   * 执行工具（接收 JsonNode 结构化参数）.
   *
   * @param input 入参 JsonNode
   * @return 执行结果（失败时标记可重试 retryable=true）
   */
  public ToolResult execute(JsonNode input) {
    return client
        .callTool(spec.name(), input)
        .map(ToolResult::success)
        .orElseGet(() -> ToolResult.failure("MCP 调用失败", true));
  }
}
