package com.oryxos.core.react.impl;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;
import com.oryxos.core.prompt.PromptBuilder;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.provider.ProviderService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReAct 循环核心调度实现.
 *
 * <p>自实现数十行紧凑 Java 逻辑，完整掌控 Agent 推理、工具调度、上下文累积与死循环防御.
 *
 * @author oryxos
 */
public class ReActLoopImpl implements ReActLoop {

  private static final Logger log = LoggerFactory.getLogger(ReActLoopImpl.class);
  private static final int DEFAULT_MAX_ITERATIONS = 10;
  private static final String MAX_ITERATIONS_MESSAGE = "达到最大轮数，已停止";

  private final ProviderService providerService;
  private final PromptBuilder promptBuilder;
  private final ToolExecutor toolExecutor;

  /**
   * 构造 ReAct 循环调度引擎.
   *
   * @param providerService Provider 统一门面服务
   * @param promptBuilder Prompt 结构化拼装器
   * @param toolExecutor 工具安全执行器
   */
  public ReActLoopImpl(
      ProviderService providerService, PromptBuilder promptBuilder, ToolExecutor toolExecutor) {
    this.providerService =
        Objects.requireNonNull(providerService, "providerService must not be null");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
    this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor must not be null");
  }

  @Override
  public String run(Session session, String userMessage, Profile profile) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(profile, "profile must not be null");

    session.append(userMessage);

    int maxIterations = resolveMaxIterations(profile);

    for (int i = 0; i < maxIterations; i++) {
      ChatRequest prompt = promptBuilder.build(session, profile);
      ChatResponse resp = providerService.chat(session.getId(), profile, prompt);
      session.append(resp);

      if (!resp.hasToolCalls()) {
        return resp.getContent() != null ? resp.getContent() : "";
      }

      for (ToolCallIntent call : resp.getToolCalls()) {
        ToolResult result = toolExecutor.execute(session.getId(), call);
        session.appendToolResult(call, result);
      }
    }

    log.warn(
        "ReAct loop for session {} (profile {}) reached max iterations ({})",
        session.getId(),
        profile.getName(),
        maxIterations);
    return MAX_ITERATIONS_MESSAGE;
  }

  private int resolveMaxIterations(Profile profile) {
    int iterations = profile.getSettings().getMaxIterations();
    return iterations > 0 ? iterations : DEFAULT_MAX_ITERATIONS;
  }
}
