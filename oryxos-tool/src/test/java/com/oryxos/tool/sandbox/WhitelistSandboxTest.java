package com.oryxos.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * WhitelistSandbox 安全沙箱验收 Harness 测试套件.
 *
 * <p>遵循安全测试核心原则：重点验证"绕得过绕不过"，覆盖路径穿越、通配符域名边界伪装及全闭防御.
 *
 * @author OryxOS Team
 */
class WhitelistSandboxTest {

  @TempDir Path tempDir;

  private Path workspaceDir;
  private WhitelistSandbox sandbox;

  @BeforeEach
  void setUp() throws Exception {
    workspaceDir = tempDir.resolve("workspace");
    Files.createDirectories(workspaceDir);

    FileSandboxProperties fileProps = new FileSandboxProperties(List.of(workspaceDir.toString()));
    ShellSandboxProperties shellProps = new ShellSandboxProperties(List.of("ls", "git", "echo"));
    HttpSandboxProperties httpProps =
        new HttpSandboxProperties(List.of("api.openai.com", "*.example.com"));

    sandbox = new WhitelistSandbox(fileProps, shellProps, httpProps);
  }

  @Test
  @DisplayName("文件白名单内放行正常访问")
  void 文件白名单内放行正常访问() {
    Path validFile = workspaceDir.resolve("data.txt");
    SandboxAction readAction = new SandboxAction(ActionType.FILE_READ, validFile.toString());
    SandboxAction writeAction = new SandboxAction(ActionType.FILE_WRITE, validFile.toString());

    assertDoesNotThrow(() -> sandbox.enforce(readAction));
    assertDoesNotThrow(() -> sandbox.enforce(writeAction));
  }

  @Test
  @DisplayName("文件白名单外路径访问必须被拒绝")
  void 文件白名单外路径访问必须被拒绝() {
    Path outsideFile = tempDir.resolve("outside.txt");
    SandboxAction action = new SandboxAction(ActionType.FILE_READ, outsideFile.toString());

    SandboxViolationException ex =
        assertThrows(SandboxViolationException.class, () -> sandbox.enforce(action));
    assertThat(ex.getMessage()).contains("路径不在白名单内");
  }

  @Test
  @DisplayName("相对路径穿越必须被拦")
  void 相对路径穿越必须被拦() {
    // 白名单只有 workspace，构造 .. 序列爬到白名单之外
    String traversalPath = workspaceDir.resolve("../../outside/secret.txt").toString();
    SandboxAction action = new SandboxAction(ActionType.FILE_READ, traversalPath);

    assertThrows(SandboxViolationException.class, () -> sandbox.enforce(action));
  }

  @Test
  @DisplayName("Shell命令白名单内正常放行")
  void Shell命令白名单内正常放行() {
    SandboxAction action1 = new SandboxAction(ActionType.SHELL_COMMAND, "ls -la");
    SandboxAction action2 = new SandboxAction(ActionType.SHELL_COMMAND, "  git status ");
    SandboxAction action3 = new SandboxAction(ActionType.SHELL_COMMAND, "echo hello");

    assertDoesNotThrow(() -> sandbox.enforce(action1));
    assertDoesNotThrow(() -> sandbox.enforce(action2));
    assertDoesNotThrow(() -> sandbox.enforce(action3));
  }

  @Test
  @DisplayName("Shell命令白名单外必须被拦截")
  void Shell命令白名单外必须被拦截() {
    SandboxAction action = new SandboxAction(ActionType.SHELL_COMMAND, "rm -rf /");

    SandboxViolationException ex =
        assertThrows(SandboxViolationException.class, () -> sandbox.enforce(action));
    assertThat(ex.getMessage()).contains("命令不在白名单内");
  }

  @Test
  @DisplayName("Shell空命令或无效输入拦截")
  void Shell空命令或无效输入拦截() {
    SandboxAction blankAction = new SandboxAction(ActionType.SHELL_COMMAND, "   ");

    assertThrows(SandboxViolationException.class, () -> sandbox.enforce(blankAction));
  }

  @Test
  @DisplayName("HTTP精确域名白名单内放行")
  void HTTP精确域名白名单内放行() {
    SandboxAction action =
        new SandboxAction(ActionType.HTTP_REQUEST, "https://api.openai.com/v1/chat");

    assertDoesNotThrow(() -> sandbox.enforce(action));
  }

  @Test
  @DisplayName("通配符域名_不能被形似域名绕过")
  void 通配符域名_不能被形似域名绕过() {
    // 白名单：*.example.com
    assertDoesNotThrow(
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.example.com/x")));

    // evil-example.com 以 "example.com" 结尾但不是子域！
    assertThrows(
        SandboxViolationException.class,
        () ->
            sandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://evil-example.com/x")));
  }

  @Test
  @DisplayName("HTTP非白名单域名必须被拦截")
  void HTTP非白名单域名必须被拦截() {
    SandboxAction action =
        new SandboxAction(ActionType.HTTP_REQUEST, "https://unknown-domain.org/api");

    SandboxViolationException ex =
        assertThrows(SandboxViolationException.class, () -> sandbox.enforce(action));
    assertThat(ex.getMessage()).contains("域名不在白名单内");
  }

  @Test
  @DisplayName("配置为空全闭原则")
  void 配置为空全闭原则() {
    WhitelistSandbox closedSandbox =
        new WhitelistSandbox(
            new FileSandboxProperties(List.of()),
            new ShellSandboxProperties(List.of()),
            new HttpSandboxProperties(List.of()));

    assertThrows(
        SandboxViolationException.class,
        () ->
            closedSandbox.enforce(
                new SandboxAction(ActionType.FILE_READ, workspaceDir.toString())));
    assertThrows(
        SandboxViolationException.class,
        () -> closedSandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, "ls")));
    assertThrows(
        SandboxViolationException.class,
        () ->
            closedSandbox.enforce(
                new SandboxAction(ActionType.HTTP_REQUEST, "https://api.openai.com")));
  }

  @Test
  @DisplayName("非阻断式check检查返回预期布尔值")
  void 非阻断式check检查返回预期布尔值() {
    assertThat(sandbox.check(workspaceDir.toString())).isTrue();
    assertThat(sandbox.check(tempDir.resolve("outside.txt").toString())).isFalse();
  }
}
