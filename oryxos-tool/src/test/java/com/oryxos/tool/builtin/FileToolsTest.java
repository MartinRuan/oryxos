package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FileTools 单元与契约测试.
 *
 * @author OryxOS Team
 */
class FileToolsTest {

  @TempDir Path tempDir;

  private Sandbox sandbox;
  private FileTools fileTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    fileTools = new FileTools(sandbox);
  }

  @Test
  @DisplayName("read_file 正常读取文件内容")
  void readFile_应能读取文件内容() throws Exception {
    Path testFile = tempDir.resolve("hello.txt");
    Files.writeString(testFile, "Hello OryxOS!");

    OryxTool readTool = fileTools.getReadFileTool();
    ToolResult result =
        readTool.execute("{\"path\":\"" + testFile.toString().replace("\\", "\\\\") + "\"}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).isEqualTo("Hello OryxOS!");
    verify(sandbox).enforce(any());
  }

  @Test
  @DisplayName("read_file 命中白名单外路径应被拦截")
  void readFile_命中白名单外路径应被拦截() {
    doThrow(new RuntimeException("Sandbox violation: path not allowed"))
        .when(sandbox)
        .enforce(any());

    OryxTool readTool = fileTools.getReadFileTool();
    assertThatThrownBy(() -> readTool.execute("{\"path\":\"/etc/shadow\"}"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");
  }

  @Test
  @DisplayName("write_file 正常写入文件")
  void writeFile_应能写入文件() throws Exception {
    Path targetFile = tempDir.resolve("subdir").resolve("output.txt");

    OryxTool writeTool = fileTools.getWriteFileTool();
    String inputJson =
        "{\"path\":\""
            + targetFile.toString().replace("\\", "\\\\")
            + "\",\"content\":\"Write Success\"}";
    ToolResult result = writeTool.execute(inputJson);

    assertThat(result.isSuccess()).isTrue();
    assertThat(Files.readString(targetFile)).isEqualTo("Write Success");
    verify(sandbox).enforce(any());
  }

  @Test
  @DisplayName("write_file 命中白名单外路径应被拦截且不创建文件")
  void writeFile_命中白名单外路径应被拦截() {
    Path forbiddenFile = tempDir.resolve("forbidden.txt");
    doThrow(new RuntimeException("Sandbox violation: path not allowed"))
        .when(sandbox)
        .enforce(any());

    OryxTool writeTool = fileTools.getWriteFileTool();
    String inputJson =
        "{\"path\":\""
            + forbiddenFile.toString().replace("\\", "\\\\")
            + "\",\"content\":\"Should not exist\"}";

    assertThatThrownBy(() -> writeTool.execute(inputJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");

    assertThat(Files.exists(forbiddenFile)).isFalse();
  }

  @Test
  @DisplayName("list_dir 正常列出目录内容")
  void listDir_应能列出目录内容() throws Exception {
    Files.createFile(tempDir.resolve("file1.txt"));
    Files.createDirectory(tempDir.resolve("subfolder"));

    OryxTool listTool = fileTools.getListDirTool();
    ToolResult result =
        listTool.execute("{\"path\":\"" + tempDir.toString().replace("\\", "\\\\") + "\"}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).contains("file1.txt").contains("subfolder");
    verify(sandbox).enforce(any());
  }

  @Test
  @DisplayName("list_dir 命中白名单外路径应被拦截")
  void listDir_命中白名单外路径应被拦截() {
    doThrow(new RuntimeException("Sandbox violation: path not allowed"))
        .when(sandbox)
        .enforce(any());

    OryxTool listTool = fileTools.getListDirTool();
    assertThatThrownBy(() -> listTool.execute("{\"path\":\"/root\"}"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");
  }
}
