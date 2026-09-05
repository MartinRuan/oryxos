package com.oryxos.tool.mcp;

import com.oryxos.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * MCP 客户端管理服务.
 *
 * <p>读取 .oryxos/mcp_servers.yaml 配置，在启动时连接外部 MCP Server 并将工具注册进 ToolRegistry； 具备容错隔离韧性：当单点 MCP
 * Server 离线或失联时，记录 WARN 告警并安全跳过，绝不阻断主程序启动.
 *
 * @author OryxOS Team
 */
@Component
public class McpClientService {

  private static final Logger log = LoggerFactory.getLogger(McpClientService.class);
  private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
  private static final Path DEFAULT_CONFIG_PATH = Path.of(".oryxos", "mcp_servers.yaml");
  private static final String KEY_MCP_SERVERS = "mcp_servers";
  private static final String KEY_NAME = "name";
  private static final String KEY_TRANSPORT = "transport";
  private static final String KEY_COMMAND = "command";
  private static final String KEY_URL = "url";
  private static final String KEY_ARGS = "args";
  private static final String KEY_ENV = "env";
  private static final String DEFAULT_TRANSPORT = "stdio";

  private final ToolRegistry toolRegistry;
  private final McpClientFactory clientFactory;
  private final Path configPath;

  /**
   * 默认构造器（使用默认工厂与标准路径）.
   *
   * @param toolRegistry 工具注册表
   */
  @Autowired
  public McpClientService(ToolRegistry toolRegistry) {
    this(toolRegistry, McpClientService::createDefaultClient, DEFAULT_CONFIG_PATH);
  }

  /**
   * 完整构造器（供测试定制工厂与路径）.
   *
   * @param toolRegistry 工具注册表
   * @param clientFactory 客户端工厂
   * @param configPath 配置文件路径
   */
  public McpClientService(
      ToolRegistry toolRegistry, McpClientFactory clientFactory, Path configPath) {
    this.toolRegistry = toolRegistry;
    this.clientFactory = clientFactory;
    this.configPath = configPath != null ? configPath : DEFAULT_CONFIG_PATH;
  }

  /** 系统启动后自动连接所有配置的 MCP 服务并注册工具. */
  @PostConstruct
  public void connectAll() {
    List<McpServerConfig> configs = loadConfigs();
    for (McpServerConfig cfg : configs) {
      try {
        McpClient client = clientFactory.create(cfg);
        List<McpToolSpec> toolSpecs = client.listTools();
        if (toolSpecs != null) {
          for (McpToolSpec spec : toolSpecs) {
            toolRegistry.register(new McpToolAdapter(client, spec));
          }
        }
      } catch (Exception e) {
        log.warn("MCP server {} 连接失败，跳过它的工具", cfg.name(), e);
      }
    }
  }

  /**
   * 从配置文件加载 MCP 服务配置列表.
   *
   * @return MCP 服务配置列表
   */
  public List<McpServerConfig> loadConfigs() {
    if (!Files.exists(configPath)) {
      return Collections.emptyList();
    }

    try (InputStream is = Files.newInputStream(configPath)) {
      Yaml yaml = new Yaml();
      Map<String, Object> root = yaml.load(is);
      if (root == null || !root.containsKey(KEY_MCP_SERVERS)) {
        return Collections.emptyList();
      }

      Object serversObj = root.get(KEY_MCP_SERVERS);
      if (!(serversObj instanceof List<?> list)) {
        return Collections.emptyList();
      }

      List<McpServerConfig> configs = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          configs.add(parseServerConfig(map));
        }
      }
      return configs;
    } catch (IOException | RuntimeException e) {
      log.warn("解析 MCP 配置文件 {} 失败: {}", configPath, e.getMessage());
      return Collections.emptyList();
    }
  }

  McpServerConfig parseServerConfig(Map<?, ?> map) {
    String name = map.get(KEY_NAME) != null ? map.get(KEY_NAME).toString() : "";
    Object transportObj = map.get(KEY_TRANSPORT);
    String transport = transportObj != null ? transportObj.toString() : DEFAULT_TRANSPORT;
    String command = map.get(KEY_COMMAND) != null ? map.get(KEY_COMMAND).toString() : null;
    String url = map.get(KEY_URL) != null ? map.get(KEY_URL).toString() : null;

    List<String> args = new ArrayList<>();
    if (map.get(KEY_ARGS) instanceof List<?> rawArgs) {
      for (Object arg : rawArgs) {
        if (arg != null) {
          args.add(resolveEnv(arg.toString()));
        }
      }
    }

    Map<String, String> env = Collections.emptyMap();
    if (map.get(KEY_ENV) instanceof Map<?, ?> rawEnv) {
      int initialCapacity = (int) Math.ceil(rawEnv.size() / 0.75) + 1;
      Map<String, String> resolvedEnv = new HashMap<>(Math.max(initialCapacity, 16));
      for (Map.Entry<?, ?> entry : rawEnv.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          resolvedEnv.put(entry.getKey().toString(), resolveEnv(entry.getValue().toString()));
        }
      }
      env = resolvedEnv;
    }

    return new McpServerConfig(name, transport, command, args, url, env);
  }

  private String resolveEnv(String value) {
    if (value == null) {
      return null;
    }
    Matcher matcher = ENV_PATTERN.matcher(value);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String varName = matcher.group(1);
      String envVal = System.getenv(varName);
      if (envVal == null) {
        envVal = System.getProperty(varName, "");
      }
      matcher.appendReplacement(sb, Matcher.quoteReplacement(envVal));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static McpClient createDefaultClient(McpServerConfig config) {
    // 基础客户端连接实现，占位返回空工具列表客户端
    return new DefaultMcpClient();
  }

  private static class DefaultMcpClient implements McpClient {

    @Override
    public List<McpToolSpec> listTools() {
      return Collections.emptyList();
    }

    @Override
    public java.util.Optional<String> callTool(
        String name, com.fasterxml.jackson.databind.JsonNode input) {
      return java.util.Optional.empty();
    }
  }
}
