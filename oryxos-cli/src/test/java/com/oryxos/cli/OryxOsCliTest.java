package com.oryxos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * OryxOS CLI 命令行主入口验收测试.
 *
 * @author OryxOS Team
 */
class OryxOsCliTest {

  @Test
  @DisplayName("验证 --help 包含所有子命令与描述信息")
  void 验证帮助信息包含所有子命令() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    StringWriter out = new StringWriter();
    cmd.setOut(new PrintWriter(out));

    int exitCode = cmd.execute("--help");
    assertEquals(0, exitCode);

    String helpText = out.toString();
    assertTrue(helpText.contains("chat"), "必须包含 chat 命令");
    assertTrue(helpText.contains("init"), "必须包含 init 命令");
    assertTrue(helpText.contains("status"), "必须包含 status 命令");
    assertTrue(helpText.contains("serve"), "必须包含 serve 命令");
    assertTrue(helpText.contains("gateway"), "必须包含 gateway 命令");
    assertTrue(helpText.contains("profile"), "必须包含 profile 命令");
    assertTrue(helpText.contains("provider"), "必须包含 provider 命令");
    assertTrue(helpText.contains("tool"), "必须包含 tool 命令");
    assertTrue(helpText.contains("session"), "必须包含 session 命令");
  }

  @Test
  @DisplayName("验证轻命令 status 执行成功")
  void 验证轻命令status执行成功() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    StringWriter out = new StringWriter();
    cmd.setOut(new PrintWriter(out));

    int exitCode = cmd.execute("status");
    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("验证轻命令 provider list 执行成功")
  void 验证轻命令providerList执行成功() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    int exitCode = cmd.execute("provider", "list");
    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("验证轻命令 tool list 执行成功")
  void 验证轻命令toolList执行成功() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    int exitCode = cmd.execute("tool", "list");
    assertEquals(0, exitCode);
  }

  @Test
  @DisplayName("验证轻命令 profile --help 执行成功")
  void 验证轻命令profileHelp执行成功() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    StringWriter out = new StringWriter();
    cmd.setOut(new PrintWriter(out));

    int exitCode = cmd.execute("profile", "--help");
    assertEquals(0, exitCode);

    String helpText = out.toString();
    assertTrue(helpText.contains("list"));
    assertTrue(helpText.contains("create"));
    assertTrue(helpText.contains("show"));
    assertTrue(helpText.contains("delete"));
  }

  @Test
  @DisplayName("验证 --version 返回版本号")
  void 验证Version输出() {
    CommandLine cmd = new CommandLine(new OryxOsCli());
    StringWriter out = new StringWriter();
    cmd.setOut(new PrintWriter(out));

    int exitCode = cmd.execute("--version");
    assertEquals(0, exitCode);

    String versionText = out.toString();
    assertTrue(versionText.contains("0.1.0-SNAPSHOT"));
  }
}
