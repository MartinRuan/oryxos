package com.oryxos.cli.command;

import java.nio.file.Files;
import java.nio.file.Paths;
import picocli.CommandLine.Command;

/**
 * status 子命令：查看 OryxOS 系统与工作区运行状态（轻命令）.
 *
 * @author OryxOS Team
 */
@Command(name = "status", description = "查看 OryxOS 系统与工作区运行状态", mixinStandardHelpOptions = true)
public class StatusCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("=== OryxOS System Status ===");
    System.out.println("Version: 0.1.0-SNAPSHOT");
    System.out.println("Java Version: " + System.getProperty("java.version"));
    System.out.println(
        "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

    boolean wsExists = Files.exists(Paths.get(".oryxos"));
    System.out.println(
        "Workspace (.oryxos): " + (wsExists ? "Initialized" : "Not Found (Run 'oryxos init')"));

    if (wsExists) {
      boolean dbExists = Files.exists(Paths.get(".oryxos", "oryxos.db"));
      System.out.println(
          "Database (SQLite): "
              + (dbExists ? "Present" : "Not yet created (Initialized on first heavy run)"));
    }
  }
}
