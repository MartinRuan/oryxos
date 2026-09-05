package com.oryxos.provider.adapter;

import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Function Calling 适配器. 负责将 OryxTool / ToolDefinition 转换为 Spring AI 原始 FunctionTool Schema
 * （不注册任何回调），并从模型响应中提取 ToolCallIntent.
 *
 * <p>关键设计：使用 {@link OpenAiApi.FunctionTool}（原始 JSON Schema）替代 {@code FunctionCallback}， 彻底杜绝 Spring
 * AI {@code AbstractToolCallSupport} 的内部自动工具执行，确保工具调度权 100% 归属 {@code ReActLoop} + {@code
 * ToolExecutor}。
 *
 * @author oryxos
 */
public final class FunctionCallingAdapter {

  private FunctionCallingAdapter() {
    // Utility class
  }

  /**
   * 将 ToolDefinition 列表转换为 Spring AI 的 FunctionTool 原始 Schema 列表. 不注册任何 FunctionCallback， Spring AI
   * 仅将 schema 发送给 LLM，不会自动执行任何工具.
   *
   * @param tools 工具定义列表
   * @return FunctionTool 列表（仅包含 Schema，无回调）
   */
  public static List<OpenAiApi.FunctionTool> toFunctionTools(List<ToolDefinition> tools) {
    if (tools == null || tools.isEmpty()) {
      return Collections.emptyList();
    }
    List<OpenAiApi.FunctionTool> functionTools = new ArrayList<>();
    for (ToolDefinition tool : tools) {
      if (tool == null) {
        continue;
      }
      String jsonSchema = tool.getInputJsonSchema();
      OpenAiApi.FunctionTool.Function function =
          new OpenAiApi.FunctionTool.Function(tool.getDescription(), tool.getName(), jsonSchema);
      functionTools.add(new OpenAiApi.FunctionTool(function));
    }
    return functionTools;
  }

  /**
   * 从 Spring AI 的 ChatResponse 中解析提取 ToolCallIntent 列表.
   *
   * @param springAiResponse Spring AI 模型响应
   * @return 工具调用意图列表
   */
  public static List<ToolCallIntent> extractToolCalls(ChatResponse springAiResponse) {
    if (springAiResponse == null || springAiResponse.getResults() == null) {
      return Collections.emptyList();
    }
    List<ToolCallIntent> intents = new ArrayList<>();
    for (Generation generation : springAiResponse.getResults()) {
      if (generation == null || generation.getOutput() == null) {
        continue;
      }
      AssistantMessage assistantMessage = generation.getOutput();
      if (assistantMessage.getToolCalls() != null) {
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
          intents.add(new ToolCallIntent(toolCall.id(), toolCall.name(), toolCall.arguments()));
        }
      }
    }
    return Collections.unmodifiableList(intents);
  }
}
