package com.oryxos.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.ToolResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * McpToolAdapter 契约与执行测试.
 *
 * @author OryxOS Team
 */
class McpToolAdapterTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private McpClient client;
  private McpToolSpec spec;
  private McpToolAdapter adapter;

  @BeforeEach
  void setUp() {
    client = mock(McpClient.class);
    spec =
        new McpToolSpec(
            "github_create_issue",
            "在 GitHub 上创建一个 Issue",
            "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}");
    adapter = new McpToolAdapter(client, spec);
  }

  @Test
  @DisplayName("契约三件套直接对齐 McpToolSpec")
  void 契约三件套直接映射() {
    assertThat(adapter.getName()).isEqualTo("github_create_issue");
    assertThat(adapter.getDescription()).isEqualTo("在 GitHub 上创建一个 Issue");
    assertThat(adapter.getInputSchema()).contains("title");
  }

  @Test
  @DisplayName("execute 调用成功时包装为 ToolResult.success")
  void execute_调用成功包装为Success() {
    when(client.callTool(eq("github_create_issue"), any()))
        .thenReturn(Optional.of("{\"issue_id\": 101, \"status\": \"created\"}"));

    ToolResult result = adapter.execute("{\"title\":\"Bug in production\"}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).contains("101");
    verify(client).callTool(eq("github_create_issue"), any());
  }

  @Test
  @DisplayName("execute 远程调用失败时返回可重试的 ToolResult.failure")
  void execute_调用失败返回可重试Failure() {
    when(client.callTool(eq("github_create_issue"), any())).thenReturn(Optional.empty());

    ToolResult result = adapter.execute("{\"title\":\"Bug in production\"}");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.isRetryable()).isTrue();
    assertThat(result.getErrorMessage()).contains("MCP 调用失败");
  }
}
