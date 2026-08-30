package com.oryxos.core.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;
import com.oryxos.core.tool.impl.ToolExecutorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

  @Mock private ToolAuditRecorder auditRecorder;
  @Mock private OryxTool mockFileTool;

  private ToolExecutorImpl toolExecutor;

  @BeforeEach
  void setUp() {
    toolExecutor = new ToolExecutorImpl(auditRecorder);
    when(mockFileTool.getName()).thenReturn("read_file");
    toolExecutor.registerTool(mockFileTool);
  }

  @Test
  @DisplayName("工具执行成功时记录审计 success=true")
  void 成功写审计_success_true() {
    ToolCallIntent call =
        new ToolCallIntent("call-1", "read_file", "{\"path\":\"/tmp/config.json\"}");

    when(mockFileTool.execute("{\"path\":\"/tmp/config.json\"}"))
        .thenReturn(ToolResult.success("{\"port\":8080}"));

    ToolResult result = toolExecutor.execute("sess-01", call);

    assertNotNull(result);
    assertTrue(result.isSuccess());
    assertEquals("{\"port\":8080}", result.getContent());

    verify(auditRecorder, times(1))
        .record(
            eq("sess-01"),
            eq("read_file"),
            eq("{\"path\":\"/tmp/config.json\"}"),
            eq("{\"port\":8080}"),
            eq(true),
            isNull(),
            anyLong());
  }

  @Test
  @DisplayName("工具执行失败或抛异常时记录 success=false 且异常不吞")
  void 失败也写_success_false_带原因_异常不吞() {
    ToolCallIntent call =
        new ToolCallIntent("call-fail", "read_file", "{\"path\":\"/root/secret.key\"}");

    when(mockFileTool.execute(anyString()))
        .thenThrow(new SecurityException("Access denied to secret.key"));

    SecurityException ex =
        assertThrows(SecurityException.class, () -> toolExecutor.execute("sess-02", call));

    assertTrue(ex.getMessage().contains("Access denied to secret.key"));

    verify(auditRecorder, times(1))
        .record(
            eq("sess-02"),
            eq("read_file"),
            eq("{\"path\":\"/root/secret.key\"}"),
            isNull(),
            eq(false),
            contains("Access denied to secret.key"),
            anyLong());
  }

  @Test
  @DisplayName("调用未注册的工具时记录失败审计并返回失败结果")
  void 工具未注册_写失败审计且返回failure() {
    ToolCallIntent call =
        new ToolCallIntent("call-unknown", "unregistered_tool", "{\"action\":\"clean\"}");

    ToolResult result = toolExecutor.execute("sess-03", call);

    assertNotNull(result);
    assertFalse(result.isSuccess());
    assertTrue(result.getErrorMessage().contains("Tool not found: unregistered_tool"));

    verify(auditRecorder, times(1))
        .record(
            eq("sess-03"),
            eq("unregistered_tool"),
            eq("{\"action\":\"clean\"}"),
            isNull(),
            eq(false),
            contains("Tool not found"),
            anyLong());
  }
}
