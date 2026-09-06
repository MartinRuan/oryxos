package com.oryxos.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 长期记忆 Agent 工具集.
 *
 * <p>为 Agent 提供自主读写长期记忆的能力（save_memory 与 recall_memory），适配 OryxTool 统一执行契约.
 *
 * @author OryxOS Team
 */
@Component
public class MemoryTools {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String PARAM_CONTENT = "content";
  private static final String PARAM_SCOPE = "scope";
  private static final String PARAM_KEYWORD = "keyword";
  private static final String SCOPE_CORE = "CORE";
  private static final String MSG_REMEMBERED = "已记住";
  private static final String MSG_NOT_FOUND = "没有找到相关记忆";
  private static final String MSG_EMPTY_CONTENT = "记忆内容不能为空";
  private static final String TOOL_SAVE_NAME = "save_memory";
  private static final String TOOL_SAVE_DESC = "记住一件值得长期记住的事";
  private static final String TOOL_RECALL_NAME = "recall_memory";
  private static final String TOOL_RECALL_DESC = "按关键词检索长期记忆";

  private final MemoryService memoryService;
  private final OryxTool saveMemoryTool;
  private final OryxTool recallMemoryTool;

  /**
   * 构造 MemoryTools 组件.
   *
   * @param memoryService 记忆系统门面服务
   */
  public MemoryTools(MemoryService memoryService) {
    this.memoryService = Objects.requireNonNull(memoryService, "memoryService must not be null");
    this.saveMemoryTool = new SaveMemoryTool();
    this.recallMemoryTool = new RecallMemoryTool();
  }

  /**
   * 保存一条记忆（供 Agent 显式调用或内部便捷调用）.
   *
   * @param content 要记住的内容
   * @param scope core 或 archival，缺省为 archival
   * @return 处理结果提示词
   */
  public String saveMemory(String content, String scope) {
    if (content == null || content.isBlank()) {
      return MSG_EMPTY_CONTENT;
    }
    MemoryScope targetScope = resolveScope(scope);
    memoryService.remember(content.trim(), targetScope);
    return MSG_REMEMBERED;
  }

  /**
   * 按关键词检索长期记忆（供 Agent 显式调用或内部便捷调用）.
   *
   * @param keyword 检索关键词
   * @return 检索结果拼接字符串或友好未命中提示
   */
  public String recallMemory(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return MSG_NOT_FOUND;
    }
    List<String> hits = memoryService.recall(keyword.trim());
    if (hits == null || hits.isEmpty()) {
      return MSG_NOT_FOUND;
    }
    return String.join("\n", hits);
  }

  /**
   * 获取保存记忆工具实例.
   *
   * @return save_memory 工具
   */
  public OryxTool getSaveMemoryTool() {
    return saveMemoryTool;
  }

  /**
   * 获取检索记忆工具实例.
   *
   * @return recall_memory 工具
   */
  public OryxTool getRecallMemoryTool() {
    return recallMemoryTool;
  }

  /**
   * 获取所管理的全部记忆工具集合.
   *
   * @return 工具列表
   */
  public List<OryxTool> getTools() {
    return List.of(saveMemoryTool, recallMemoryTool);
  }

  private MemoryScope resolveScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return MemoryScope.ARCHIVAL;
    }
    String normalized = scope.trim().toUpperCase(Locale.ROOT);
    if (SCOPE_CORE.equals(normalized)) {
      return MemoryScope.CORE;
    }
    return MemoryScope.ARCHIVAL;
  }

  private class SaveMemoryTool implements OryxTool {

    private static final String SCHEMA =
        "{\"type\":\"object\",\"properties\":{"
            + "\"content\":{\"type\":\"string\",\"description\":\"要记住的内容\"},"
            + "\"scope\":{\"type\":\"string\","
            + "\"description\":\"core 或 archival，不确定就填 archival\"}},"
            + "\"required\":[\"content\"]}";

    @Override
    public String getName() {
      return TOOL_SAVE_NAME;
    }

    @Override
    public String getDescription() {
      return TOOL_SAVE_DESC;
    }

    @Override
    public String getInputSchema() {
      return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
      try {
        JsonNode root = OBJECT_MAPPER.readTree(inputJson != null ? inputJson : "{}");
        JsonNode contentNode = root.get(PARAM_CONTENT);
        if (contentNode == null || contentNode.asText().isBlank()) {
          return ToolResult.failure("缺少必需参数: " + PARAM_CONTENT);
        }
        String content = contentNode.asText();
        JsonNode scopeNode = root.get(PARAM_SCOPE);
        String scope = scopeNode != null ? scopeNode.asText() : null;

        String result = saveMemory(content, scope);
        return ToolResult.success(result);
      } catch (JsonProcessingException | RuntimeException e) {
        return ToolResult.failure("参数解析或保存失败: " + e.getMessage());
      }
    }
  }

  private class RecallMemoryTool implements OryxTool {

    private static final String SCHEMA =
        "{\"type\":\"object\",\"properties\":{"
            + "\"keyword\":{\"type\":\"string\",\"description\":\"检索关键词\"}},"
            + "\"required\":[\"keyword\"]}";

    @Override
    public String getName() {
      return TOOL_RECALL_NAME;
    }

    @Override
    public String getDescription() {
      return TOOL_RECALL_DESC;
    }

    @Override
    public String getInputSchema() {
      return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
      try {
        JsonNode root = OBJECT_MAPPER.readTree(inputJson != null ? inputJson : "{}");
        JsonNode keywordNode = root.get(PARAM_KEYWORD);
        if (keywordNode == null || keywordNode.asText().isBlank()) {
          return ToolResult.failure("缺少必需参数: " + PARAM_KEYWORD);
        }
        String keyword = keywordNode.asText();
        String result = recallMemory(keyword);
        return ToolResult.success(result);
      } catch (JsonProcessingException | RuntimeException e) {
        return ToolResult.failure("参数解析或检索失败: " + e.getMessage());
      }
    }
  }
}
