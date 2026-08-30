package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/**
 * tool 父命令及 list 子命令：查看可用工具列表（轻命令）.
 *
 * @author OryxOS Team
 */
@Command(
    name = "tool",
    description = "查看系统中注册的可用 Tool 列表与 Schema",
    mixinStandardHelpOptions = true,
    subcommands = {ToolCommand.ListSubcommand.class})
public class ToolCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("Use 'oryxos tool list' to view available tools.");
  }

  /** tool list 子命令. */
  @Command(name = "list", description = "列出系统内置与已注册的工具列表", mixinStandardHelpOptions = true)
  public static class ListSubcommand implements Runnable {
    @Override
    public void run() {
      System.out.println("Built-in Core Tools in OryxOS:");
      System.out.println("  - read_file     (读取本地文件内容，路径白名单与防越界校验)");
      System.out.println("  - write_file    (写入本地文件内容，路径白名单校验)");
      System.out.println("  - list_dir      (列出目录文件清单，路径白名单校验)");
      System.out.println("  - shell         (安全执行 Shell 命令，精准白名单与 argv 直传)");
      System.out.println("  - http_get      (发送 HTTP GET 请求，SSRF 安全防护)");
      System.out.println("  - http_post     (发送 HTTP POST 请求，域名白名单校验)");
      System.out.println("  - save_memory   (写入长期记忆，显式指定分区)");
      System.out.println("  - recall_memory (检索归档记忆，三路加权召回)");
      System.out.println("  - notify        (推送通知至 Webhook / 飞书 / 企微 / 钉钉)");
    }
  }
}
