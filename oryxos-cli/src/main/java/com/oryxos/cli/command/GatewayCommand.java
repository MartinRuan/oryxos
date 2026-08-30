package com.oryxos.cli.command;

import com.oryxos.cli.launcher.SpringContextLauncher;
import picocli.CommandLine.Command;

/**
 * gateway 子命令：以守护进程模式启动，同时挂载多个 Channel（重命令，启动 Spring 上下文）.
 *
 * @author OryxOS Team
 */
@Command(name = "gateway", description = "以守护进程模式启动，同时挂载多个交互渠道", mixinStandardHelpOptions = true)
public class GatewayCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("Starting OryxOS Gateway daemon...");
    SpringContextLauncher.getOrCreateContext();
    System.out.println("OryxOS Gateway daemon is running. Multi-channel listeners active.");
  }
}
