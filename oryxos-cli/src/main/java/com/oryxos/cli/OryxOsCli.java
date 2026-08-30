package com.oryxos.cli;

import com.oryxos.cli.command.ChatCommand;
import com.oryxos.cli.command.GatewayCommand;
import com.oryxos.cli.command.InitCommand;
import com.oryxos.cli.command.ProfileCommand;
import com.oryxos.cli.command.ProviderCommand;
import com.oryxos.cli.command.ServeCommand;
import com.oryxos.cli.command.SessionCommand;
import com.oryxos.cli.command.StatusCommand;
import com.oryxos.cli.command.ToolCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * OryxOS CLI 主命令行入口.
 *
 * <p>挂载 12 个运维与交互子命令，支持轻重命令分流.
 *
 * @author OryxOS Team
 */
@Command(
    name = "oryxos",
    mixinStandardHelpOptions = true,
    version = "oryxos 0.1.0-SNAPSHOT",
    description = "Distributed AI Agent OS Command Line Interface",
    subcommands = {
      ChatCommand.class,
      InitCommand.class,
      StatusCommand.class,
      ServeCommand.class,
      GatewayCommand.class,
      ProfileCommand.class,
      ProviderCommand.class,
      ToolCommand.class,
      SessionCommand.class,
      CommandLine.HelpCommand.class
    })
public class OryxOsCli implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  /**
   * CLI 命令行 main 入口.
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new OryxOsCli()).execute(args);
    System.exit(exitCode);
  }
}
