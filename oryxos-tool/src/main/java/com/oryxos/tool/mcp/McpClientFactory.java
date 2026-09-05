package com.oryxos.tool.mcp;

/**
 * MCP 客户端工厂契约.
 *
 * @author OryxOS Team
 */
@FunctionalInterface
public interface McpClientFactory {

  /**
   * 根据配置创建 MCP 客户端实例.
   *
   * @param config MCP 服务配置
   * @return McpClient 实例
   * @throws Exception 当连接失败时抛出
   */
  McpClient create(McpServerConfig config) throws Exception;
}
