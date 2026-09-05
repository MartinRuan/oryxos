package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

  private final Sandbox sandbox;

  /**
   * 构造 ShellTools.
   *
   * @param sandbox 沙箱安全检查器
   */
  public ShellTools(Sandbox sandbox) {
    this.sandbox = sandbox;
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

    List<String> commandLine = new ArrayList<>();
    commandLine.add(command);
    commandLine.addAll(args);

    Process process = null;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
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
}
