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
 * OryxOS CLI 根命令定义.
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
public class OryxCliCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
