package com.oryxos.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

/**
 * MCP 客户端抽象契约.
 *
 * @author OryxOS Team
 */
public interface McpClient {

  /**
   * 拉取远端 MCP 服务暴露的工具规范列表.
   *
   * @return 工具规范列表
   * @throws Exception 当网络或连接异常时抛出
   */
  List<McpToolSpec> listTools() throws Exception;

  /**
   * 调用远端 MCP 服务的具体工具.
   *
   * @param name 工具名称
   * @param input 入参 JsonNode
   * @return 工具执行返回结果字符串（若调用失败返回 Optional.empty()）
   */
  Optional<String> callTool(String name, JsonNode input);
}
