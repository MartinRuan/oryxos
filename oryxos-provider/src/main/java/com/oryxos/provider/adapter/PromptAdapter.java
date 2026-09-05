package com.oryxos.provider.adapter;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.MessageType;
import com.oryxos.core.model.ToolCallIntent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 负责将 OryxOS 的统一 ChatRequest 转换为 Spring AI 的 Prompt 对象.
 *
 * @author oryxos
 */
public final class PromptAdapter {

  private static final String DEFAULT_TOOL_CALL_ID = "default_tool_call_id";
  private static final String ROLE_FUNCTION = "function";
  private static final String ROLE_TOOL = "tool";

  private PromptAdapter() {
    // Utility class
  }

  /**
   * 将 ChatRequest 适配转换为 Spring AI Prompt.
   *
   * @param request 统一对话请求
   * @param resolvedModel 最终解析生效的模型名称
   * @return Spring AI Prompt 对象
   */
  public static Prompt toSpringAiPrompt(ChatRequest request, String resolvedModel) {
    List<Message> springAiMessages = new ArrayList<>();

    for (ChatMessage msg : request.getMessages()) {
      if (msg == null) {
        continue;
      }
      MessageType role = msg.getRole() != null ? msg.getRole() : MessageType.USER;
      switch (role) {
        case SYSTEM:
          springAiMessages.add(new SystemMessage(msg.getContent()));
          break;
        case ASSISTANT:
          if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            List<AssistantMessage.ToolCall> springAiToolCalls = new ArrayList<>();
            for (ToolCallIntent intent : msg.getToolCalls()) {
              springAiToolCalls.add(
                  new AssistantMessage.ToolCall(
                      intent.getId() != null ? intent.getId() : "",
                      ROLE_FUNCTION,
                      intent.getName(),
                      intent.getArgumentsJson()));
            }
            springAiMessages.add(
                new AssistantMessage(msg.getContent(), Collections.emptyMap(), springAiToolCalls));
          } else {
            springAiMessages.add(new AssistantMessage(msg.getContent()));
          }
          break;
        case TOOL:
          String toolId = msg.getToolCallId() != null ? msg.getToolCallId() : DEFAULT_TOOL_CALL_ID;
          ToolResponseMessage.ToolResponse toolResponse =
              new ToolResponseMessage.ToolResponse(toolId, ROLE_TOOL, msg.getContent());
          springAiMessages.add(new ToolResponseMessage(List.of(toolResponse)));
          break;
        case USER:
        default:
          springAiMessages.add(new UserMessage(msg.getContent()));
          break;
      }
    }

    OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
    if (resolvedModel != null && !resolvedModel.isBlank()) {
      optionsBuilder.withModel(resolvedModel);
    }
    if (request.getTemperature() != null) {
      optionsBuilder.withTemperature(request.getTemperature().floatValue());
    }
    if (request.getMaxTokens() != null) {
      optionsBuilder.withMaxTokens(request.getMaxTokens());
    }
    if (request.getTools() != null && !request.getTools().isEmpty()) {
      List<OpenAiApi.FunctionTool> functionTools =
          FunctionCallingAdapter.toFunctionTools(request.getTools());
      optionsBuilder.withTools(functionTools);
    }

    return new Prompt(springAiMessages, optionsBuilder.build());
  }
}
