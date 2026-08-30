package com.oryxos.provider.adapter;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

/**
 * 工具 Schema 适配器. 负责将 OryxTool 列表翻译转换为 Spring AI 的 FunctionCallback 定义. 契约保证：只做 Schema
 * 描述翻译，产物中绝不包含任何工具执行逻辑.
 *
 * @author oryxos
 */
@Component
public class ToolSchemaAdapter {

  /**
   * 将 OryxTool 集合翻译为 Spring AI 的 FunctionCallback 列表.
   *
   * @param tools OryxTool 集合
   * @return Spring AI FunctionCallback 列表
   */
  public List<FunctionCallback> toSpringAiTools(Collection<OryxTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return Collections.emptyList();
    }
    List<FunctionCallback> callbacks = new ArrayList<>();
    for (OryxTool tool : tools) {
      if (tool == null) {
        continue;
      }
      callbacks.add(
          new ReadOnlyToolCallback(tool.getName(), tool.getDescription(), tool.getInputSchema()));
    }
    return Collections.unmodifiableList(callbacks);
  }

  /**
   * 将 ToolDefinition 列表转换为 Spring AI 的 FunctionCallback 列表.
   *
   * @param toolDefinitions 工具定义列表
   * @return Spring AI FunctionCallback 列表
   */
  public List<FunctionCallback> fromToolDefinitions(List<ToolDefinition> toolDefinitions) {
    if (toolDefinitions == null || toolDefinitions.isEmpty()) {
      return Collections.emptyList();
    }
    List<FunctionCallback> callbacks = new ArrayList<>();
    for (ToolDefinition def : toolDefinitions) {
      if (def == null) {
        continue;
      }
      callbacks.add(
          new ReadOnlyToolCallback(def.getName(), def.getDescription(), def.getInputJsonSchema()));
    }
    return Collections.unmodifiableList(callbacks);
  }

  /** 只读工具回调实现，仅向 LLM 提供 Schema 格式定义，禁止自动执行. */
  public static final class ReadOnlyToolCallback implements FunctionCallback {

    private final String name;
    private final String description;
    private final String inputTypeSchema;

    /**
     * 构造只读 Schema 回调.
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param inputTypeSchema 参数 JSON Schema
     */
    public ReadOnlyToolCallback(String name, String description, String inputTypeSchema) {
      this.name = name;
      this.description = description;
      this.inputTypeSchema = inputTypeSchema != null ? inputTypeSchema : "{}";
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
      // 关掉自动执行：实际执行交回 ReActLoop 与 ToolExecutor 调度
      return "{\"status\":\"deferred_to_react_loop\"}";
    }
  }
}
