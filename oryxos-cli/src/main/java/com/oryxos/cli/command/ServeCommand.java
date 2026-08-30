package com.oryxos.cli.command;

import com.oryxos.cli.launcher.SpringContextLauncher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * serve 子命令：启动 OryxOS Web Service REST API 服务（重命令，启动 Spring 上下文）.
 *
 * @author OryxOS Team
 */
@Command(
    name = "serve",
    description = "启动 OryxOS Web Service REST API 服务",
    mixinStandardHelpOptions = true)
public class ServeCommand implements Runnable {

  @Option(
      names = {"-p", "--port"},
      description = "指定 HTTP 监听端口 (默认: 8080)",
      defaultValue = "8080")
  private int port = 8080;

  @Override
  public void run() {
    System.out.println("Starting OryxOS Web Service on port: " + port + " ...");
    System.setProperty("server.port", String.valueOf(port));
    SpringContextLauncher.getOrCreateContext();
    System.out.println("OryxOS Web Service started successfully. Listening for requests...");
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }
}
