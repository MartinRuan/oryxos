package com.oryxos.tool.mcp;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 外部 MCP 服务配置模型.
 *
 * @param name 服务唯一标识名称
 * @param transport 通信协议类型（stdio 或 sse）
 * @param command 启动命令程序名（stdio 模式必填）
 * @param args 启动参数列表
 * @param url 服务端点 URL（sse 模式可选）
 * @param env 环境变量映射表
 * @author OryxOS Team
 */
public record McpServerConfig(
    String name,
    String transport,
    String command,
    List<String> args,
    String url,
    Map<String, String> env)
    implements Serializable {

  /** 紧凑构造函数，保证集合字段非空. */
  public McpServerConfig {
    args = args != null ? List.copyOf(args) : Collections.emptyList();
    env = env != null ? Map.copyOf(env) : Collections.emptyMap();
  }
}
