package com.oryxos.provider.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ToolCallIntent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * PromptAdapter 与运行时参数适配测试.
 *
 * @author oryxos
 */
class ModelOptionsAdapterTest {

  @Test
  @DisplayName("验证多轮消息转换与运行时 temperature/maxTokens 选项绑定")
  void testPromptOptionsAndMessages() {
    ChatRequest request =
        ChatRequest.builder()
            .provider("qwen")
            .model("qwen-max")
            .message(ChatMessage.system("You are an assistant"))
            .message(ChatMessage.user("Hello"))
            .message(
                ChatMessage.assistant(
                    "Call tool", List.of(new ToolCallIntent("id-1", "read_file", "{}"))))
            .message(ChatMessage.tool("id-1", "file content"))
            .temperature(0.5)
            .maxTokens(1024)
            .build();

    Prompt prompt = PromptAdapter.toSpringAiPrompt(request, "qwen-max");

    assertThat(prompt.getInstructions()).hasSize(4);
    assertThat(prompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
    assertThat(prompt.getInstructions().get(1).getMessageType()).isEqualTo(MessageType.USER);
    assertThat(prompt.getInstructions().get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    assertThat(prompt.getInstructions().get(3).getMessageType()).isEqualTo(MessageType.TOOL);

    assertThat(prompt.getOptions().getModel()).isEqualTo("qwen-max");
    assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.5f);
    assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(1024);
  }
}
