package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ShellTools 单元测试.
 *
 * @author OryxOS Team
 */
class ShellToolsTest {

  private Sandbox sandbox;
  private ShellTools shellTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    shellTools = new ShellTools(sandbox);
  }

  @Test
  @DisplayName("shell 正常执行合法命令并返回输出")
  void shell_正常执行命令应返回输出() {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    String inputJson =
        isWindows
            ? "{\"command\":\"cmd.exe\",\"args\":[\"/c\",\"echo\",\"HelloOryxOS\"]}"
            : "{\"command\":\"echo\",\"args\":[\"HelloOryxOS\"]}";

    ToolResult result = shellTools.execute(inputJson);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).contains("HelloOryxOS");
    verify(sandbox)
        .enforce(
            argThat(
                a ->
                    a.type() == ActionType.SHELL_COMMAND
                        && (isWindows ? "cmd.exe".equals(a.target()) : "echo".equals(a.target()))));
  }

  @Test
  @DisplayName("shell 命中白名单外命令应被拦截")
  void shell_命中白名单外命令应被拦截() {
    doThrow(new RuntimeException("Sandbox violation: command not allowed"))
        .when(sandbox)
        .enforce(any());

    String inputJson = "{\"command\":\"rm\",\"args\":[\"-rf\",\"/\"]}";

    assertThatThrownBy(() -> shellTools.execute(inputJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");
  }

  @Test
  @DisplayName("shell 工具契约三件套满足要求")
  void shell_契约三件套非空() {
    assertThat(shellTools.getName()).isEqualTo("shell");
    assertThat(shellTools.getDescription()).isNotBlank();
    assertThat(shellTools.getInputSchema()).contains("command");
  }
}
