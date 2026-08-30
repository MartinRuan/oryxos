package com.oryxos.provider.mock;

import com.oryxos.core.model.ToolCallIntent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 标准化离线 Mock ChatModel 实现. 支持预设回复文本与预设工具调用列表，用于脱机单元测试与 CI 流水线.
 *
 * @author oryxos
 */
public class MockChatModel implements ChatModel {

  private static final String DEFAULT_REPLY = "Mock assistant response";
  private static final String DEFAULT_MODEL = "mock-model";
  private static final String ROLE_FUNCTION = "function";
  private static final String FINISH_STOP = "STOP";
  private static final String FINISH_TOOL_CALLS = "TOOL_CALLS";

  private final String presetResponse;
  private final List<ToolCallIntent> presetToolCalls;

  /** 构造默认 Mock 模型. */
  public MockChatModel() {
    this(DEFAULT_REPLY, Collections.emptyList());
  }

  /**
   * 构造指定文本回复的 Mock 模型.
   *
   * @param presetResponse 预设回复文本
   */
  public MockChatModel(String presetResponse) {
    this(presetResponse, Collections.emptyList());
  }

  /**
   * 构造指定文本和工具调用的 Mock 模型.
   *
   * @param presetResponse 预设回复文本
   * @param presetToolCalls 预设工具调用列表
   */
  public MockChatModel(String presetResponse, List<ToolCallIntent> presetToolCalls) {
    this.presetResponse = Objects.requireNonNullElse(presetResponse, DEFAULT_REPLY);
    this.presetToolCalls =
        presetToolCalls != null ? List.copyOf(presetToolCalls) : Collections.emptyList();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
    for (ToolCallIntent intent : presetToolCalls) {
      toolCalls.add(
          new AssistantMessage.ToolCall(
              intent.getId() != null ? intent.getId() : "mock_call_id",
              ROLE_FUNCTION,
              intent.getName(),
              intent.getArgumentsJson()));
    }

    AssistantMessage assistantMessage =
        new AssistantMessage(presetResponse, Collections.emptyMap(), toolCalls);

    String finishReason = toolCalls.isEmpty() ? FINISH_STOP : FINISH_TOOL_CALLS;
    ChatGenerationMetadata genMetadata = ChatGenerationMetadata.from(finishReason, null);
    Generation generation = new Generation(assistantMessage, genMetadata);

    MockUsage usage = new MockUsage(10L, 20L, 30L);

    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder().withUsage(usage).withModel(DEFAULT_MODEL).build();

    return new ChatResponse(List.of(generation), metadata);
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return ChatOptionsBuilder.builder().withModel(DEFAULT_MODEL).build();
  }

  /** Mock 用量统计静态内部类. */
  private static class MockUsage implements Usage {
    private final Long promptTokens;
    private final Long generationTokens;
    private final Long totalTokens;

    MockUsage(Long promptTokens, Long generationTokens, Long totalTokens) {
      this.promptTokens = promptTokens;
      this.generationTokens = generationTokens;
      this.totalTokens = totalTokens;
    }

    @Override
    public Long getPromptTokens() {
      return promptTokens;
    }

    @Override
    public Long getGenerationTokens() {
      return generationTokens;
    }

    @Override
    public Long getTotalTokens() {
      return totalTokens;
    }
  }
}
