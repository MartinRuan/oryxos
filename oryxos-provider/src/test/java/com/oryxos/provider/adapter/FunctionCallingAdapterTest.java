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
import org.springframework.ai.model.function.FunctionCallback;

/**
 * FunctionCallingAdapter 转换与意图解析单元测试.
 *
 * @author oryxos
 */
class FunctionCallingAdapterTest {

  @Test
  @DisplayName("验证 ToolDefinition 转换为 Spring AI FunctionCallback 契约")
  void testToFunctionCallbacks() {
    ToolDefinition tool =
        ToolDefinition.builder()
            .name("read_file")
            .description("Read file content")
            .inputJsonSchema(
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")
            .build();

    List<FunctionCallback> callbacks = FunctionCallingAdapter.toFunctionCallbacks(List.of(tool));

    assertThat(callbacks).hasSize(1);
    FunctionCallback callback = callbacks.get(0);
    assertThat(callback.getName()).isEqualTo("read_file");
    assertThat(callback.getDescription()).isEqualTo("Read file content");
    assertThat(callback.getInputTypeSchema()).contains("path");

    // 验证调用时绝不执行实际工具逻辑
    String result = callback.call("{}");
    assertThat(result).contains("deferred_to_react_loop");
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
