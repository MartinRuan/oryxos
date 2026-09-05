package com.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.tool.ToolRegistry;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * McpClientService 单元与容错隔离测试.
 *
 * @author OryxOS Team
 */
class McpClientServiceTest {

  @TempDir Path tempDir;

  private ToolRegistry toolRegistry;
  private Path configFile;

  @BeforeEach
  void setUp() throws Exception {
    toolRegistry = new ToolRegistry();
    configFile = tempDir.resolve("mcp_servers.yaml");

    String yamlContent =
        """
        mcp_servers:
          - name: good_server
            transport: stdio
            command: node
          - name: bad_server
            transport: sse
            url: http://localhost:9999/sse
        """;
    Files.writeString(configFile, yamlContent);
  }

  @Test
  @DisplayName("loadConfigs 正常解析 YAML 配置文件")
  void loadConfigs_正常解析配置() {
    McpClientService service =
        new McpClientService(toolRegistry, cfg -> mock(McpClient.class), configFile);

    List<McpServerConfig> configs = service.loadConfigs();

    assertThat(configs).hasSize(2);
    assertThat(configs.get(0).name()).isEqualTo("good_server");
    assertThat(configs.get(1).name()).isEqualTo("bad_server");
  }

  @Test
  @DisplayName("某个MCP_server失联_不能拖垮启动和其他工具")
  void 某个MCP_server失联_不能拖垮启动和其他工具() throws Exception {
    McpClient goodClient = mock(McpClient.class);
    when(goodClient.listTools())
        .thenReturn(List.of(new McpToolSpec("good_mcp_tool", "正常工具", "{}")));

    McpClient badClient = mock(McpClient.class);
    when(badClient.listTools()).thenThrow(new ConnectException("refused"));

    McpClientFactory factory =
        cfg -> {
          if ("good_server".equals(cfg.name())) {
            return goodClient;
          } else {
            return badClient;
          }
        };

    McpClientService service = new McpClientService(toolRegistry, factory, configFile);

    // 核心断言：connectAll 绝不抛异常，外部失联不拖垮本地启动
    assertThatCode(service::connectAll).doesNotThrowAnyException();

    // 好的 server 工具照常注册进注册表
    assertThat(toolRegistry.contains("good_mcp_tool")).isTrue();
    // 坏的 server 工具未注册
    assertThat(toolRegistry.contains("bad_mcp_tool")).isFalse();
  }
}
