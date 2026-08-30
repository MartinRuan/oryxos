package com.oryxos.provider.adapter;

import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * Function Calling 适配器. 负责将 OryxTool / ToolDefinition 转换为 Spring AI 工具定义 Schema， 并从模型响应中提取
 * ToolCallIntent，显式禁用底层框架自动执行.
 *
 * @author oryxos
 */
public final class FunctionCallingAdapter {

  private FunctionCallingAdapter() {
    // Utility class
  }

  /**
   * 将 ToolDefinition 列表转换为 Spring AI 的 FunctionCallback 契约声明. 注意：此处生成的 FunctionCallback
   * 不包含实际执行体，仅用于 Schema 导出与协议生成.
   *
   * @param tools 工具定义列表
   * @return FunctionCallback 列表
   */
  public static List<FunctionCallback> toFunctionCallbacks(List<ToolDefinition> tools) {
    if (tools == null || tools.isEmpty()) {
      return Collections.emptyList();
    }
    List<FunctionCallback> callbacks = new ArrayList<>();
    for (ToolDefinition tool : tools) {
      if (tool == null) {
        continue;
      }
      callbacks.add(
          new ToolSchemaCallback(tool.getName(), tool.getDescription(), tool.getInputJsonSchema()));
    }
    return callbacks;
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

  /** 仅用于向模型声明 Schema 的非执行 Callback. */
  private static final class ToolSchemaCallback implements FunctionCallback {

    private final String name;
    private final String description;
    private final String inputTypeSchema;

    private ToolSchemaCallback(String name, String description, String inputTypeSchema) {
      this.name = name;
      this.description = description;
      this.inputTypeSchema = inputTypeSchema;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getInputTypeSchema() {
      return inputTypeSchema;
    }

    @Override
    public String call(String functionInput) {
      // 严禁底层框架自动执行工具，调度权归 ReActLoop + ToolExecutor 统一管理
      return "{\"status\":\"deferred_to_react_loop\"}";
    }
  }
}
