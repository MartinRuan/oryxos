package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 内置文件操作工具集（包含 read_file, write_file, list_dir）.
 *
 * <p>所有文件操作在执行物理 IO 前，必须首行调用 Sandbox.enforce 进行白名单安全检查.
 *
 * @author OryxOS Team
 */
@Component
public class FileTools {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PARAM_PATH = "path";
  private static final String PARAM_CONTENT = "content";

  private final Sandbox sandbox;
  private final OryxTool readFileTool;
  private final OryxTool writeFileTool;
  private final OryxTool listDirTool;

  /**
   * 构造 FileTools.
   *
   * @param sandbox 沙箱安全检查器
   */
  public FileTools(Sandbox sandbox) {
    this.sandbox = sandbox;
    this.readFileTool = new ReadFileTool();
    this.writeFileTool = new WriteFileTool();
    this.listDirTool = new ListDirTool();
  }

  /**
   * 获取读取文件工具.
   *
   * @return read_file 工具
   */
  public OryxTool getReadFileTool() {
    return readFileTool;
  }

  /**
   * 获取写入文件工具.
   *
   * @return write_file 工具
   */
  public OryxTool getWriteFileTool() {
    return writeFileTool;
  }

  /**
   * 获取列出目录工具.
   *
   * @return list_dir 工具
   */
  public OryxTool getListDirTool() {
    return listDirTool;
  }

  /**
   * 获取本组件提供的全部工具实例.
   *
   * @return 工具列表
   */
  public List<OryxTool> getTools() {
    return List.of(readFileTool, writeFileTool, listDirTool);
  }

  private class ReadFileTool implements OryxTool {
    @Override
    public String getName() {
      return "read_file";
    }

    @Override
    public String getDescription() {
      return "读取指定路径的文件内容";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{"
          + "\"path\":{\"type\":\"string\",\"description\":\"文件绝对或相对路径\"}},"
          + "\"required\":[\"path\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = parsePath(inputJson);
      // 首行强制沙箱检查
      sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));

      try {
        Path target = Path.of(path);
        if (!Files.exists(target)) {
          return ToolResult.failure("File not found: " + path, false);
        }
        String content = Files.readString(target);
        return ToolResult.success(content);
      } catch (IOException e) {
        return ToolResult.failure("Failed to read file " + path + ": " + e.getMessage(), false);
      }
    }
  }

  private class WriteFileTool implements OryxTool {
    @Override
    public String getName() {
      return "write_file";
    }

    @Override
    public String getDescription() {
      return "将指定内容写入目标文件";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{"
          + "\"path\":{\"type\":\"string\",\"description\":\"目标文件路径\"},"
          + "\"content\":{\"type\":\"string\",\"description\":\"待写入的文件内容\"}},"
          + "\"required\":[\"path\",\"content\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = parsePath(inputJson);
      String content = parseContent(inputJson);

      // 首行强制沙箱检查
      sandbox.enforce(new SandboxAction(ActionType.FILE_WRITE, path));

      try {
        Path target = Path.of(path);
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
          Files.createDirectories(parent);
        }
        Files.writeString(target, content);
        return ToolResult.success("File written successfully: " + path);
      } catch (IOException e) {
        return ToolResult.failure("Failed to write file " + path + ": " + e.getMessage(), false);
      }
    }
  }

  private class ListDirTool implements OryxTool {
    @Override
    public String getName() {
      return "list_dir";
    }

    @Override
    public String getDescription() {
      return "列出指定目录下的文件和子目录列表";
    }

    @Override
    public String getInputSchema() {
      return "{\"type\":\"object\",\"properties\":{"
          + "\"path\":{\"type\":\"string\",\"description\":\"目标目录路径\"}},"
          + "\"required\":[\"path\"]}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      String path = parsePath(inputJson);
      // 首行强制沙箱检查
      sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));

      try {
        Path target = Path.of(path);
        if (!Files.exists(target)) {
          return ToolResult.failure("Directory not found: " + path, false);
        }
        if (!Files.isDirectory(target)) {
          return ToolResult.failure("Target path is not a directory: " + path, false);
        }
        try (Stream<Path> stream = Files.list(target)) {
          String listing =
              stream
                  .map(
                      p -> {
                        Path fileName = p.getFileName();
                        String name = fileName != null ? fileName.toString() : p.toString();
                        return name + (Files.isDirectory(p) ? "/" : "");
                      })
                  .collect(Collectors.joining("\n"));
          return ToolResult.success(listing);
        }
      } catch (IOException e) {
        String msg = "Failed to list directory " + path + ": " + e.getMessage();
        return ToolResult.failure(msg, false);
      }
    }
  }

  private static String parsePath(String inputJson) {
    if (inputJson == null || inputJson.isBlank()) {
      return "";
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(inputJson);
      if (node.has(PARAM_PATH)) {
        return node.get(PARAM_PATH).asText();
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // ignore
    }
    return inputJson.trim();
  }

  private static String parseContent(String inputJson) {
    if (inputJson == null || inputJson.isBlank()) {
      return "";
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(inputJson);
      if (node.has(PARAM_CONTENT)) {
        return node.get(PARAM_CONTENT).asText();
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // ignore
    }
    return "";
  }
}
