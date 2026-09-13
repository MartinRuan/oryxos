package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    doThrow(new SandboxViolationException("Sandbox violation: command not allowed"))
        .when(sandbox)
        .enforce(any());

    String inputJson = "{\"command\":\"rm\",\"args\":[\"-rf\",\"/\"]}";

    assertThatThrownBy(() -> shellTools.execute(inputJson))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("Sandbox violation");
  }

  @Test
  @DisplayName("解释器只能执行当前 Agent scripts 目录里的脚本")
  void interpreter_只允许当前Agent脚本目录(@TempDir Path tempDir) throws Exception {
    Path scripts = tempDir.resolve(".oryxos/agents/report-agent/scripts");
    Files.createDirectories(scripts);
    Files.writeString(scripts.resolve("report.py"), "print('ok')");
    Files.writeString(tempDir.resolve("outside.py"), "print('outside')");
    ProfileContext.set(
        Profile.builder()
            .name("report-agent")
            .provider(new Profile.ProviderConfig("deepseek", "model", 0.2))
            .build());
    ShellTools scopedTools = new ShellTools(sandbox, tempDir);
    try {
      ToolResult allowed =
          scopedTools.execute(
              """
              {"command":"python3","args":["scripts/report.py"]}
              """);
      assertThat(allowed.isSuccess()).isTrue();
      assertThat(allowed.getContent()).contains("ok");

      assertThatThrownBy(
              () ->
                  scopedTools.execute(
                      """
                      {"command":"python3","args":["../../../outside.py"]}
                      """))
          .isInstanceOf(SandboxViolationException.class)
          .hasMessageContaining("scripts");
      assertThatThrownBy(
              () ->
                  scopedTools.execute(
                      """
                      {"command":"python3","args":["-c","print(1)"]}
                      """))
          .isInstanceOf(SandboxViolationException.class)
          .hasMessageContaining("script");
    } finally {
      ProfileContext.clear();
    }
  }

  @Test
  @DisplayName("shell 工具契约三件套满足要求")
  void shell_契约三件套非空() {
    assertThat(shellTools.getName()).isEqualTo("shell");
    assertThat(shellTools.getDescription()).isNotBlank();
    assertThat(shellTools.getInputSchema()).contains("command");
  }
}
