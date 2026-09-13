package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 内置系统命令执行工具（shell）.
 *
 * <p>遵循安全原则：可执行文件精确匹配白名单、argv 直接传递（不经 Shell 解释）、超时强制终止保护.
 *
 * @author OryxOS Team
 */
@Component
public class ShellTools implements OryxTool {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int DEFAULT_TIMEOUT_SECONDS = 10;
  private static final String PARAM_COMMAND = "command";
  private static final String PARAM_ARGS = "args";
  private static final String ARGUMENT_OPTION_PREFIX = "-";
  private static final Set<String> SCRIPT_INTERPRETERS = Set.of("python", "python3", "bash");

  private final Sandbox sandbox;
  private final Path baseWorkspaceDir;

  /**
   * 构造 ShellTools.
   *
   * @param sandbox 沙箱安全检查器
   */
  @Autowired
  public ShellTools(Sandbox sandbox) {
    this(sandbox, Path.of("."));
  }

  ShellTools(Sandbox sandbox, Path baseWorkspaceDir) {
    this.sandbox = sandbox;
    this.baseWorkspaceDir =
        baseWorkspaceDir != null ? baseWorkspaceDir.toAbsolutePath().normalize() : Path.of(".");
  }

  public Sandbox getSandbox() {
    return sandbox;
  }

  @Override
  public String getName() {
    return "shell";
  }

  @Override
  public String getDescription() {
    return "执行指定的系统可执行命令并返回输出（直传 argv，带超时控制）";
  }

  @Override
  public String getInputSchema() {
    return "{\"type\":\"object\",\"properties\":{"
        + "\"command\":{\"type\":\"string\",\"description\":\"要执行的可执行命令文件名\"},"
        + "\"args\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},"
        + "\"description\":\"命令参数列表\"}},"
        + "\"required\":[\"command\"]}";
  }

  @Override
  public ToolResult execute(String inputJson) {
    String command = "";
    List<String> args = new ArrayList<>();

    if (inputJson != null && !inputJson.isBlank()) {
      try {
        JsonNode node = OBJECT_MAPPER.readTree(inputJson);
        if (node.has(PARAM_COMMAND)) {
          command = node.get(PARAM_COMMAND).asText();
        }
        if (node.has(PARAM_ARGS) && node.get(PARAM_ARGS).isArray()) {
          for (JsonNode argNode : node.get(PARAM_ARGS)) {
            args.add(argNode.asText());
          }
        }
      } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
        command = inputJson.trim();
      }
    }

    // 首行强制沙箱检查
    sandbox.enforce(new SandboxAction(ActionType.SHELL_COMMAND, command));
    Path agentWorkingDirectory = resolveAgentWorkingDirectory();
    if (isScriptInterpreter(command)) {
      Path script = requireAgentScript(args, agentWorkingDirectory);
      sandbox.enforce(new SandboxAction(ActionType.FILE_READ, script.toString()));
    }

    List<String> commandLine = new ArrayList<>();
    commandLine.add(command);
    commandLine.addAll(args);

    Process process = null;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
      if (agentWorkingDirectory != null && Files.isDirectory(agentWorkingDirectory)) {
        processBuilder.directory(agentWorkingDirectory.toFile());
      }
      processBuilder.redirectErrorStream(true);
      process = processBuilder.start();

      boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!completed) {
        process.destroyForcibly();
        String timeoutMsg = "Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds";
        return ToolResult.failure(timeoutMsg, false);
      }

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.exitValue();
      String outputText = output.toString().trim();
      if (exitCode == 0) {
        return ToolResult.success(outputText);
      } else {
        return ToolResult.failure(
            "Command exited with code " + exitCode + ": " + outputText, false);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return ToolResult.failure("Command execution interrupted: " + e.getMessage(), false);
    } catch (IOException e) {
      return ToolResult.failure("Failed to execute command: " + e.getMessage(), false);
    }
  }

  private boolean isScriptInterpreter(String command) {
    try {
      Path commandPath = Path.of(command);
      Path fileName = commandPath.getFileName();
      return fileName != null && SCRIPT_INTERPRETERS.contains(fileName.toString());
    } catch (java.nio.file.InvalidPathException e) {
      return false;
    }
  }

  private Path requireAgentScript(List<String> args, Path agentDirectory) {
    if (agentDirectory == null
        || args.isEmpty()
        || args.get(0).startsWith(ARGUMENT_OPTION_PREFIX)) {
      throw new com.oryxos.tool.sandbox.SandboxViolationException(
          "Interpreter requires a script file in the current Agent scripts directory");
    }
    Path scriptsDirectory = agentDirectory.resolve("scripts").normalize();
    Path rawScript = Path.of(args.get(0));
    Path candidate =
        rawScript.isAbsolute()
            ? rawScript.normalize()
            : agentDirectory.resolve(rawScript).normalize();
    try {
      Path realScriptsDirectory = scriptsDirectory.toRealPath();
      Path realScript = candidate.toRealPath();
      if (!Files.isRegularFile(realScript) || !realScript.startsWith(realScriptsDirectory)) {
        throw new com.oryxos.tool.sandbox.SandboxViolationException(
            "Interpreter script must be inside current Agent scripts directory: " + args.get(0));
      }
      return realScript;
    } catch (IOException e) {
      throw new com.oryxos.tool.sandbox.SandboxViolationException(
          "Interpreter script is unavailable in current Agent scripts directory: " + args.get(0),
          e);
    }
  }

  private Path resolveAgentWorkingDirectory() {
    Profile profile = ProfileContext.current();
    if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
      return null;
    }
    Path agentsRoot = baseWorkspaceDir.resolve(".oryxos").resolve("agents").normalize();
    Path agentDirectory = agentsRoot.resolve(profile.getName().trim()).normalize();
    return agentDirectory.startsWith(agentsRoot) ? agentDirectory : null;
  }
}
