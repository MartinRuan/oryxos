package com.oryxos.cli;

import picocli.CommandLine.Command;

/**
 * OryxOS CLI 主命令入口.
 *
 * @author OryxOS Team
 */
@Command(
    name = "oryxos",
    mixinStandardHelpOptions = true,
    version = "oryxos 0.1.0-SNAPSHOT",
    description = "Distributed AI Agent OS Command Line Interface")
public class OryxCliCommand implements Runnable {

  @Override
  public void run() {
    // 默认展示帮助信息由 picocli 自动处理
  }
}
