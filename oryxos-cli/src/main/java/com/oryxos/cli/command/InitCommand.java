package com.oryxos.cli.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * init 子命令：初始化 OryxOS 运行时工作区目录结构（轻命令，不启动 Spring 上下文）.
 *
 * @author OryxOS Team
 */
@Command(
    name = "init",
    description = "初始化 OryxOS 运行时工作区目录结构 (.oryxos/)",
    mixinStandardHelpOptions = true)
public class InitCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(InitCommand.class);

  @Option(
      names = {"-d", "--dir"},
      description = "工作区根目录路径 (默认: .oryxos)",
      defaultValue = ".oryxos")
  private String rootDir = ".oryxos";

  @Override
  public void run() {
    Path rootPath = Paths.get(rootDir);
    try {
      System.out.println("Initializing OryxOS workspace at: " + rootPath.toAbsolutePath());

      // 1. 创建子目录
      Files.createDirectories(rootPath.resolve("agents"));
      Files.createDirectories(rootPath.resolve("skills"));
      Files.createDirectories(rootPath.resolve("memory"));
      Files.createDirectories(rootPath.resolve("sessions"));
      Files.createDirectories(rootPath.resolve("logs"));

      // 2. 初始化初始文件 (如果不存在)
      createFileIfNotExists(
          rootPath.resolve("AGENTS.md"),
          "# OryxOS Agents Definition\n\n- Global workspace agent behaviors\n");
      createFileIfNotExists(
          rootPath.resolve("SOUL.md"),
          "# Agent Soul Definition\n\n- Persona: Helpful and rigorous Agent OS Assistant\n");
      createFileIfNotExists(
          rootPath.resolve("USER.md"),
          "# User Preferences\n\n- Language: zh-CN\n- Mode: Professional\n");
      createFileIfNotExists(
          rootPath.resolve("memory").resolve("MEMORY.md"),
          "# Long-Term Memory Store\n\n<!-- Managed by save_memory / recall_memory -->\n");

      System.out.println("OryxOS workspace successfully initialized!");
    } catch (IOException e) {
      log.error("Failed to initialize workspace at {}", rootDir, e);
      System.err.println("[Error] Failed to initialize workspace: " + e.getMessage());
    }
  }

  private void createFileIfNotExists(Path path, String defaultContent) throws IOException {
    if (!Files.exists(path)) {
      Files.writeString(
          path,
          defaultContent,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE);
    }
  }

  public String getRootDir() {
    return rootDir;
  }

  public void setRootDir(String rootDir) {
    this.rootDir = rootDir;
  }
}
