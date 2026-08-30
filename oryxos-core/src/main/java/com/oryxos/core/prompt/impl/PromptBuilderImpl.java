package com.oryxos.core.prompt.impl;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolDefinition;
import com.oryxos.core.prompt.PromptBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 结构化 Prompt 组装器标准实现.
 *
 * <p>按顺序严格组装： [1] System Prompt（角色设定 + Bootstrap/Skill 上下文 + 当前日期时间） [2] 长期记忆（预留 Memory 扩展位） [3]
 * 会话历史（按 maxHistoryTurns 进行窗口截断） [4] 可用工具列表（Function Calling Schema）
 *
 * @author oryxos
 */
public class PromptBuilderImpl implements PromptBuilder {

  private static final int DEFAULT_MAX_HISTORY_TURNS = 20;
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ContextLoader contextLoader;

  /**
   * 构造 Prompt 组装器.
   *
   * @param contextLoader 上下文加载器
   */
  public PromptBuilderImpl(ContextLoader contextLoader) {
    this.contextLoader = contextLoader;
  }

  @Override
  public ChatRequest build(Session session, Profile profile) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(profile, "profile must not be null");

    List<ChatMessage> assembledMessages = new ArrayList<>();

    // 1. 组装 System Prompt (角色设定 + Bootstrap/Skill + 当前日期时间)
    String systemPrompt = buildSystemPrompt(profile);
    if (!systemPrompt.isBlank()) {
      assembledMessages.add(ChatMessage.system(systemPrompt));
    }

    // 2. 对话历史按窗口截断 (默认最近 20 轮)
    List<ChatMessage> history = truncateHistory(session.getMessages(), profile);
    assembledMessages.addAll(history);

    // 3. 工具 Schema 映射
    List<ToolDefinition> toolDefinitions = buildToolDefinitions(profile);

    // 4. 组装统一 ChatRequest
    ChatRequest.Builder requestBuilder =
        ChatRequest.builder()
            .sessionId(session.getId())
            .messages(assembledMessages)
            .tools(toolDefinitions);

    if (profile.getProvider() != null) {
      if (profile.getProvider().getModel() != null) {
        requestBuilder.model(profile.getProvider().getModel());
      }
      if (profile.getProvider().getTemperature() != null) {
        requestBuilder.temperature(profile.getProvider().getTemperature());
      }
    }

    return requestBuilder.build();
  }

  private String buildSystemPrompt(Profile profile) {
    StringBuilder sb = new StringBuilder();

    if (profile.getIdentity() != null && profile.getIdentity().getPrompt() != null) {
      sb.append(profile.getIdentity().getPrompt().trim());
    }

    if (contextLoader != null) {
      String loadedContext = contextLoader.loadContext(profile);
      if (loadedContext != null && !loadedContext.isBlank()) {
        if (sb.length() > 0) {
          sb.append("\n\n");
        }
        sb.append(loadedContext.trim());
      }
    }

    String nowStr = LocalDateTime.now().format(DATE_TIME_FORMATTER);
    if (sb.length() > 0) {
      sb.append("\n\n");
    }
    sb.append("当前日期时间: ").append(nowStr);

    return sb.toString();
  }

  private List<ChatMessage> truncateHistory(List<ChatMessage> messages, Profile profile) {
    if (messages == null || messages.isEmpty()) {
      return Collections.emptyList();
    }

    int maxTurns = profile.getSettings().getMaxHistoryTurns();
    if (maxTurns <= 0) {
      maxTurns = DEFAULT_MAX_HISTORY_TURNS;
    }

    if (messages.size() <= maxTurns) {
      return new ArrayList<>(messages);
    }

    return new ArrayList<>(messages.subList(messages.size() - maxTurns, messages.size()));
  }

  private List<ToolDefinition> buildToolDefinitions(Profile profile) {
    if (profile.getTools() == null || profile.getTools().isEmpty()) {
      return Collections.emptyList();
    }

    List<ToolDefinition> definitions = new ArrayList<>();
    for (String toolName : profile.getTools()) {
      if (toolName != null && !toolName.isBlank()) {
        definitions.add(
            new ToolDefinition(
                toolName.trim(), "Tool for " + toolName.trim(), "{\"type\":\"object\"}"));
      }
    }
    return definitions;
  }
}
