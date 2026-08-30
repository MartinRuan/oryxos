package com.oryxos.provider.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.OryxTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * ToolSchemaAdapter 验收测试. 验证 OryxTool 的 schema 翻译成 Spring AI 格式后字段一一对齐，且只翻译、不含任何执行逻辑.
 *
 * @author oryxos
 */
class ToolSchemaAdapterTest {

  private final ToolSchemaAdapter adapter = new ToolSchemaAdapter();

  @Test
  @DisplayName("OryxTool 的 schema 翻译成 Spring AI 格式后字段一一对齐")
  void schema翻译成SpringAi格式_字段一一对齐() {
    OryxTool httpGetTool =
        new OryxTool() {
          @Override
          public String getName() {
            return "http_get";
          }

          @Override
          public String getDescription() {
            return "发起 HTTP GET 请求";
          }

          @Override
          public String getInputSchema() {
            return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}";
          }
        };

    OryxTool readFileTool =
        new OryxTool() {
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
            return "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}";
          }
        };

    List<FunctionCallback> callbacks = adapter.toSpringAiTools(List.of(httpGetTool, readFileTool));

    assertThat(callbacks).hasSize(2);

    FunctionCallback cb1 = callbacks.get(0);
    assertThat(cb1.getName()).isEqualTo("http_get");
    assertThat(cb1.getDescription()).isEqualTo("发起 HTTP GET 请求");
    assertThat(cb1.getInputTypeSchema()).contains("\"url\"");

    FunctionCallback cb2 = callbacks.get(1);
    assertThat(cb2.getName()).isEqualTo("read_file");
    assertThat(cb2.getDescription()).isEqualTo("读取指定路径的文件内容");
    assertThat(cb2.getInputTypeSchema()).contains("\"path\"");
  }

  @Test
  @DisplayName("只翻译不执行：产物 Callback 不含实际业务执行逻辑")
  void 只翻译不执行_产物不含实际业务执行() {
    OryxTool shellTool =
        new OryxTool() {
          @Override
          public String getName() {
            return "shell";
          }

          @Override
          public String getDescription() {
            return "执行安全白名单 Shell 命令";
          }
        };

    List<FunctionCallback> callbacks = adapter.toSpringAiTools(List.of(shellTool));
    assertThat(callbacks).hasSize(1);

    FunctionCallback cb = callbacks.get(0);
    // 调用 call 验证只返回 deferred 标记，绝不会触发实际 shell 执行
    String result = cb.call("{\"command\":\"ls\"}");
    assertThat(result).contains("deferred_to_react_loop");
  }
}
