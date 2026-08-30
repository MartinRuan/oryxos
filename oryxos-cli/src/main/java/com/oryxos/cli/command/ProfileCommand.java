package com.oryxos.cli.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * profile 父命令及 list, create, show, delete 子命令（轻命令，不启动 Spring 上下文）.
 *
 * @author OryxOS Team
 */
@Command(
    name = "profile",
    description = "查看与管理 Agent Profile 配置",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProfileCommand.ListSubcommand.class,
      ProfileCommand.CreateSubcommand.class,
      ProfileCommand.ShowSubcommand.class,
      ProfileCommand.DeleteSubcommand.class
    })
public class ProfileCommand implements Runnable {

  @Override
  public void run() {
    System.out.println(
        "Use 'oryxos profile --help' for available subcommands (list, create, show, delete).");
  }

  /** profile list 子命令. */
  @Command(name = "list", description = "列出当前工作区中已定义的所有 Profile", mixinStandardHelpOptions = true)
  public static class ListSubcommand implements Runnable {
    @Override
    public void run() {
      Path agentsDir = Paths.get(".oryxos", "agents");
      Path profilesDir = Paths.get(".oryxos", "profiles");

      System.out.println("Available Profiles:");
      boolean found = false;

      if (Files.exists(agentsDir) && Files.isDirectory(agentsDir)) {
        try (Stream<Path> stream = Files.list(agentsDir)) {
          stream
              .filter(Files::isDirectory)
              .forEach(
                  p -> {
                    Path fileName = p.getFileName();
                    if (fileName != null) {
                      System.out.println("  - " + fileName + " (agent directory)");
                    }
                  });
          found = true;
        } catch (IOException e) {
          System.err.println("[Error] Failed to read agents dir: " + e.getMessage());
        }
      }

      if (Files.exists(profilesDir) && Files.isDirectory(profilesDir)) {
        try (Stream<Path> stream = Files.list(profilesDir)) {
          stream
              .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
              .forEach(
                  p -> {
                    Path fileName = p.getFileName();
                    if (fileName != null) {
                      System.out.println("  - " + fileName + " (yaml profile)");
                    }
                  });
          found = true;
        } catch (IOException e) {
          System.err.println("[Error] Failed to read profiles dir: " + e.getMessage());
        }
      }

      if (!found) {
        System.out.println("  (No profiles found in .oryxos/agents or .oryxos/profiles)");
      }
    }
  }

  /** profile create 子命令. */
  @Command(name = "create", description = "创建新的 Profile 模板", mixinStandardHelpOptions = true)
  public static class CreateSubcommand implements Runnable {
    @Parameters(index = "0", description = "Profile 名称")
    private String name;

    @Override
    public void run() {
      Path agentDir = Paths.get(".oryxos", "agents", name);
      try {
        Files.createDirectories(agentDir);
        Path agentMd = agentDir.resolve("AGENT.md");
        if (Files.exists(agentMd)) {
          System.out.println("Agent [" + name + "] already exists at: " + agentMd);
          return;
        }
        String template =
            "---\n"
                + "name: "
                + name
                + "\n"
                + "description: "
                + name
                + " Agent\n"
                + "provider:\n"
                + "  name: deepseek\n"
                + "  model: deepseek-chat\n"
                + "tools:\n"
                + "  - read_file\n"
                + "  - write_file\n"
                + "  - list_dir\n"
                + "---\n\n"
                + "你是一个专业的 "
                + name
                + " 助手。\n";
        Files.writeString(agentMd, template, StandardCharsets.UTF_8);
        System.out.println("Profile [" + name + "] created successfully at: " + agentMd);
      } catch (IOException e) {
        System.err.println("[Error] Failed to create profile: " + e.getMessage());
      }
    }
  }

  /** profile show 子命令. */
  @Command(name = "show", description = "查看指定 Profile 的内容", mixinStandardHelpOptions = true)
  public static class ShowSubcommand implements Runnable {
    @Parameters(index = "0", description = "Profile 名称")
    private String name;

    @Override
    public void run() {
      Path agentMd = Paths.get(".oryxos", "agents", name, "AGENT.md");
      Path yamlFile = Paths.get(".oryxos", "profiles", name + ".yaml");

      if (Files.exists(agentMd)) {
        try {
          System.out.println("=== " + agentMd + " ===");
          System.out.println(Files.readString(agentMd, StandardCharsets.UTF_8));
        } catch (IOException e) {
          System.err.println("[Error] Failed to read " + agentMd + ": " + e.getMessage());
        }
      } else if (Files.exists(yamlFile)) {
        try {
          System.out.println("=== " + yamlFile + " ===");
          System.out.println(Files.readString(yamlFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
          System.err.println("[Error] Failed to read " + yamlFile + ": " + e.getMessage());
        }
      } else {
        System.out.println("Profile [" + name + "] not found.");
      }
    }
  }

  /** profile delete 子命令. */
  @Command(name = "delete", description = "删除指定 Profile", mixinStandardHelpOptions = true)
  public static class DeleteSubcommand implements Runnable {
    @Parameters(index = "0", description = "Profile 名称")
    private String name;

    @Override
    public void run() {
      Path agentDir = Paths.get(".oryxos", "agents", name);
      if (Files.exists(agentDir)) {
        try {
          // 递归删除或提示
          Files.walk(agentDir)
              .sorted((a, b) -> b.compareTo(a))
              .forEach(
                  p -> {
                    try {
                      Files.delete(p);
                    } catch (IOException e) {
                      System.err.println(
                          "[Warn] Could not delete: " + p + ", error: " + e.getMessage());
                    }
                  });
          System.out.println("Profile [" + name + "] deleted successfully.");
        } catch (IOException e) {
          System.err.println("[Error] Failed to delete profile: " + e.getMessage());
        }
      } else {
        System.out.println("Profile [" + name + "] not found.");
      }
    }
  }
}
