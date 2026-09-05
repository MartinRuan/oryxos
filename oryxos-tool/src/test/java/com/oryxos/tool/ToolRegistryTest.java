package com.oryxos.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ToolRegistry 核心管理与 Profile 过滤测试.
 *
 * @author OryxOS Team
 */
class ToolRegistryTest {

  private ToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ToolRegistry();
    registry.register(createDummyTool("read_file", "读取文件"));
    registry.register(createDummyTool("write_file", "写入文件"));
    registry.register(createDummyTool("shell", "执行Shell命令"));
    registry.register(createDummyTool("http_get", "发起HTTP GET"));
    registry.register(createDummyTool("mcp_github_create_issue", "MCP GitHub工具"));
  }

  @Test
  @DisplayName("register 与 getTool 能够正常存取工具")
  void 能够正常注册和获取工具() {
    Optional<OryxTool> tool = registry.getTool("read_file");
    assertThat(tool).isPresent();
    assertThat(tool.get().getName()).isEqualTo("read_file");

    assertThat(registry.contains("read_file")).isTrue();
    assertThat(registry.contains("unknown_tool")).isFalse();
  }

  @Test
  @DisplayName("getAllTools 返回全部已注册工具")
  void getAllTools_返回全部工具() {
    assertThat(registry.getAllTools())
        .hasSize(5)
        .extracting(OryxTool::getName)
        .containsExactlyInAnyOrder(
            "read_file", "write_file", "shell", "http_get", "mcp_github_create_issue");
  }

  @Test
  @DisplayName("按 Profile 的 tools 字段过滤，子集精确匹配不多不少")
  void filterTools_子集精确匹配不多不少() {
    List<String> profileTools = List.of("read_file", "http_get");

    List<OryxTool> filtered = registry.filterTools(profileTools);

    assertThat(filtered)
        .hasSize(2)
        .extracting(OryxTool::getName)
        .containsExactly("read_file", "http_get");
  }

  @Test
  @DisplayName("Profile 声明不存在工具时，只返回有效工具不多不少")
  void filterTools_忽略未注册工具() {
    List<String> profileTools = List.of("shell", "not_exist_tool");

    List<OryxTool> filtered = registry.filterTools(profileTools);

    assertThat(filtered).hasSize(1).extracting(OryxTool::getName).containsExactly("shell");
  }

  @Test
  @DisplayName("Profile tools 为空或 null 时，返回空列表")
  void filterTools_空声明返回空列表() {
    assertThat(registry.filterTools(null)).isEmpty();
    assertThat(registry.filterTools(List.of())).isEmpty();
  }

  private static OryxTool createDummyTool(String name, String description) {
    return new OryxTool() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDescription() {
        return description;
      }

      @Override
      public String getInputSchema() {
        return "{\"type\":\"object\"}";
      }

      @Override
      public ToolResult execute(String inputJson) {
        return ToolResult.success("ok");
      }
    };
  }
}
