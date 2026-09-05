package com.oryxos.provider.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * FunctionCallingAdapter 转换与意图解析单元测试.
 *
 * @author oryxos
 */
class FunctionCallingAdapterTest {

  @Test
  @DisplayName("验证 ToolDefinition 转换为 Spring AI FunctionTool 原始 Schema（无回调）")
  void testToFunctionTools() {
    ToolDefinition tool =
        ToolDefinition.builder()
            .name("read_file")
            .description("Read file content")
            .inputJsonSchema(
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")
            .build();

    List<OpenAiApi.FunctionTool> functionTools =
        FunctionCallingAdapter.toFunctionTools(List.of(tool));

    assertThat(functionTools).hasSize(1);
    OpenAiApi.FunctionTool ft = functionTools.get(0);
    assertThat(ft.type()).isEqualTo(OpenAiApi.FunctionTool.Type.FUNCTION);
    assertThat(ft.function().name()).isEqualTo("read_file");
    assertThat(ft.function().description()).isEqualTo("Read file content");
  }

  @Test
  @DisplayName("验证空或 null 工具列表返回空列表")
  void testToFunctionToolsEmpty() {
    assertThat(FunctionCallingAdapter.toFunctionTools(null)).isEmpty();
    assertThat(FunctionCallingAdapter.toFunctionTools(List.of())).isEmpty();
  }

  @Test
  @DisplayName("验证从 Spring AI ChatResponse 解析提取 ToolCallIntent")
  void testExtractToolCalls() {
    AssistantMessage.ToolCall toolCall =
        new AssistantMessage.ToolCall("call_123", "function", "shell", "{\"command\":\"ls -la\"}");
    AssistantMessage assistantMessage =
        new AssistantMessage("Thinking...", java.util.Collections.emptyMap(), List.of(toolCall));
    Generation generation = new Generation(assistantMessage);
    ChatResponse springAiResponse = new ChatResponse(List.of(generation));

    List<ToolCallIntent> intents = FunctionCallingAdapter.extractToolCalls(springAiResponse);

    assertThat(intents).hasSize(1);
    ToolCallIntent intent = intents.get(0);
    assertThat(intent.getId()).isEqualTo("call_123");
    assertThat(intent.getName()).isEqualTo("shell");
    assertThat(intent.getArgumentsJson()).isEqualTo("{\"command\":\"ls -la\"}");
  }
}
