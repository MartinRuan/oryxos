package com.oryxos.cli.command;

import com.oryxos.channel.cli.CliChannel;
import com.oryxos.cli.launcher.SpringContextLauncher;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * chat 子命令：在终端里和 Agent 交互式对话（重命令，启动 Spring 上下文）.
 *
 * @author OryxOS Team
 */
@Command(name = "chat", description = "在终端里和 Agent 交互式对话", mixinStandardHelpOptions = true)
public class ChatCommand implements Runnable {

  @Option(
      names = {"-p", "--profile"},
      description = "指定 Agent Profile 名称 (默认: default)",
      defaultValue = "default")
  private String profileName = "default";

  @Override
  public void run() {
    ConfigurableApplicationContext ctx = SpringContextLauncher.getOrCreateContext();
    CliChannel cliChannel = ctx.getBean(CliChannel.class);
    cliChannel.run(profileName);
  }

  public String getProfileName() {
    return profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }
}
