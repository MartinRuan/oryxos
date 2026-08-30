package com.oryxos.provider.impl;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.FinishReason;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.core.model.TokenUsage;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.ProviderService;
import com.oryxos.provider.adapter.ChatModelFactory;
import com.oryxos.provider.adapter.FunctionCallingAdapter;
import com.oryxos.provider.adapter.PromptAdapter;
import com.oryxos.provider.config.ProviderProperties;
import com.oryxos.provider.exception.ProviderErrorCode;
import com.oryxos.provider.exception.ProviderException;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统一 Provider 门面服务实现类. 负责 Provider 寻址、模型调用、Function Calling 协议适配、重试与审计落库.
 *
 * @author oryxos
 */
@Service
public class ProviderServiceImpl implements ProviderService {

  private static final Logger log = LoggerFactory.getLogger(ProviderServiceImpl.class);

  private static final String DEFAULT_SESSION_KEY = "sessionId";
  private static final String UNASSIGNED_SESSION = "session-unassigned";
  private static final String KEYWORD_TIMEOUT = "timeout";
  private static final String KEYWORD_CONNECTION = "connection";
  private static final String KEYWORD_500 = "500";
  private static final String KEYWORD_502 = "502";
  private static final String KEYWORD_503 = "503";
  private static final String KEYWORD_504 = "504";
  private static final String KEYWORD_RESET = "reset";

  private final ProviderRegistry providerRegistry;
  private final ProviderProperties providerProperties;
  private final Optional<LlmCallRepository> llmCallRepository;

  /**
   * 构造函数.
   *
   * @param providerRegistry Provider 显式注册中心
   * @param providerProperties Provider 配置属性
   * @param llmCallRepository 审计仓储（可选）
   */
  @Autowired
  public ProviderServiceImpl(
      ProviderRegistry providerRegistry,
      ProviderProperties providerProperties,
      @Autowired(required = false) LlmCallRepository llmCallRepository) {
    this.providerRegistry = providerRegistry;
    this.providerProperties =
        providerProperties != null ? providerProperties : new ProviderProperties();
    this.llmCallRepository = Optional.ofNullable(llmCallRepository);
  }

  @Override
  public ChatResponse chat(String sessionId, Profile profile, ChatRequest prompt) {
    if (profile == null || profile.getProvider() == null) {
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_NOT_FOUND,
          "unknown",
          "",
          "Profile and Profile.provider configuration must not be null");
    }

    String providerName = profile.getProviderName();
    String modelName = profile.getModelName();

    ChatRequest.Builder builder =
        prompt != null ? ChatRequest.builder(prompt) : ChatRequest.builder();
    builder.sessionId(sessionId);
    builder.provider(providerName);
    if (modelName != null && !modelName.isBlank()) {
      builder.model(modelName);
    }
    Double temperature = profile.getProvider().getTemperature();
    if (temperature != null) {
      builder.temperature(temperature);
    }

    return call(builder.build());
  }

  @Override
  public ChatResponse chat(String sessionId, Profile profile, String textPrompt) {
    ChatRequest.Builder builder = ChatRequest.builder();
    builder.sessionId(sessionId);
    if (textPrompt != null && !textPrompt.isBlank()) {
      builder.messages(List.of(ChatMessage.user(textPrompt)));
    }
    return chat(sessionId, profile, builder.build());
  }

  @Override
  public ChatResponse call(ChatRequest request) {
    validateRequest(request);

    String providerName = request.getProvider();
    ProviderDescriptor descriptor = getOrThrowDescriptor(providerName, request.getModel());
    ChatModel chatModel = getOrThrowModel(providerName, request.getModel());

    String resolvedModel =
        (request.getModel() != null && !request.getModel().isBlank())
            ? request.getModel()
            : descriptor.getDefaultModel();

    Prompt prompt = PromptAdapter.toSpringAiPrompt(request, resolvedModel);

    long startTime = System.currentTimeMillis();
    org.springframework.ai.chat.model.ChatResponse springAiResponse =
        executeCallWithAudit(request, providerName, resolvedModel, chatModel, prompt, startTime);

    long durationMs = System.currentTimeMillis() - startTime;
    return buildChatResponse(request, providerName, resolvedModel, springAiResponse, durationMs);
  }

  private org.springframework.ai.chat.model.ChatResponse executeCallWithAudit(
      ChatRequest request,
      String providerName,
      String resolvedModel,
      ChatModel chatModel,
      Prompt prompt,
      long startTime) {
    try {
      return invokeWithRetry(providerName, resolvedModel, chatModel, prompt);
    } catch (RuntimeException ex) {
      long durationMs = System.currentTimeMillis() - startTime;
      recordAudit(
          request.getSessionId(),
          providerName,
          resolvedModel,
          0,
          0,
          0,
          durationMs,
          false,
          ex.getMessage());
      throw ex;
    }
  }

  private ChatResponse buildChatResponse(
      ChatRequest request,
      String providerName,
      String resolvedModel,
      org.springframework.ai.chat.model.ChatResponse springAiResponse,
      long durationMs) {
    if (springAiResponse == null) {
      recordAudit(
          request.getSessionId(),
          providerName,
          resolvedModel,
          0,
          0,
          0,
          durationMs,
          false,
          "Failed to invoke LLM provider: empty response");
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE,
          providerName,
          resolvedModel,
          "Failed to invoke LLM provider: empty response");
    }

    String content = extractContent(springAiResponse);
    FinishReason finishReason = extractFinishReason(springAiResponse);
    List<ToolCallIntent> toolCalls = FunctionCallingAdapter.extractToolCalls(springAiResponse);
    if (!toolCalls.isEmpty()) {
      finishReason = FinishReason.TOOL_CALLS;
    }

    TokenUsage tokenUsage = calculateTokenUsage(springAiResponse);

    recordAudit(
        request.getSessionId(),
        providerName,
        resolvedModel,
        tokenUsage.getPromptTokens(),
        tokenUsage.getCompletionTokens(),
        tokenUsage.getTotalTokens(),
        durationMs,
        true,
        null);

    return ChatResponse.builder()
        .provider(providerName)
        .model(resolvedModel)
        .content(content)
        .toolCalls(toolCalls)
        .finishReason(finishReason)
        .usage(tokenUsage)
        .durationMs(durationMs)
        .build();
  }

  @Override
  public void registerProvider(ProviderDescriptor descriptor) {
    if (descriptor == null) {
      return;
    }
    ChatModel chatModel = ChatModelFactory.createChatModel(descriptor);
    providerRegistry.register(descriptor, chatModel);
  }

  @Override
  public Optional<ProviderDescriptor> getProvider(String providerName) {
    return providerRegistry.getDescriptor(providerName);
  }

  @Override
  public Collection<ProviderDescriptor> listProviders() {
    return providerRegistry.listDescriptors();
  }

  @Override
  public boolean isProviderAvailable(String providerName) {
    return providerRegistry.isAvailable(providerName);
  }

  private void validateRequest(ChatRequest request) {
    if (request == null) {
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_NOT_FOUND, "unknown", "", "ChatRequest must not be null");
    }
    if (request.getProvider() == null || request.getProvider().isBlank()) {
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_NOT_FOUND, "", "", "Provider name must not be blank");
    }
  }

  private ProviderDescriptor getOrThrowDescriptor(String providerName, String model) {
    return providerRegistry
        .getDescriptor(providerName)
        .orElseThrow(
            () ->
                new ProviderException(
                    ProviderErrorCode.PROVIDER_NOT_FOUND,
                    providerName,
                    model,
                    "Provider not found in registry: " + providerName));
  }

  private ChatModel getOrThrowModel(String providerName, String model) {
    return providerRegistry
        .getModel(providerName)
        .orElseThrow(
            () ->
                new ProviderException(
                    ProviderErrorCode.PROVIDER_NOT_FOUND,
                    providerName,
                    model,
                    "ChatModel not initialized for provider: " + providerName));
  }

  private org.springframework.ai.chat.model.ChatResponse invokeWithRetry(
      String providerName, String resolvedModel, ChatModel chatModel, Prompt prompt) {
    int maxRetries = providerProperties.getDefaultMaxRetries();
    int attempt = 0;
    org.springframework.ai.chat.model.ChatResponse response = null;
    Throwable lastException = null;

    while (attempt <= maxRetries) {
      try {
        log.debug(
            "Calling LLM provider: name={}, model={}, attempt={}/{}",
            providerName,
            resolvedModel,
            attempt + 1,
            maxRetries + 1);
        response = chatModel.call(prompt);
        break;
      } catch (Exception ex) {
        lastException = ex;
        attempt++;
        log.warn(
            "LLM call attempt {} failed for provider {}: {}",
            attempt,
            providerName,
            ex.getMessage());

        if (attempt <= maxRetries && isRetryable(ex)) {
          performBackoff(providerName, resolvedModel, attempt);
        } else {
          break;
        }
      }
    }

    if (response == null) {
      if (lastException instanceof ProviderException) {
        throw (ProviderException) lastException;
      }
      String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE,
          providerName,
          resolvedModel,
          "Failed to invoke LLM provider after " + attempt + " attempts: " + errorMsg,
          lastException);
    }
    return response;
  }

  private void performBackoff(String providerName, String resolvedModel, int attempt) {
    try {
      long backoffMs = (long) Math.pow(2, attempt) * 500L;
      Thread.sleep(backoffMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE,
          providerName,
          resolvedModel,
          "Interrupted during retry backoff",
          ie);
    }
  }

  private String extractContent(org.springframework.ai.chat.model.ChatResponse response) {
    if (response.getResult() != null && response.getResult().getOutput() != null) {
      String content = response.getResult().getOutput().getContent();
      return content != null ? content : "";
    }
    return "";
  }

  private FinishReason extractFinishReason(
      org.springframework.ai.chat.model.ChatResponse response) {
    if (response.getResult() != null && response.getResult().getMetadata() != null) {
      String finishReasonStr = response.getResult().getMetadata().getFinishReason();
      if (finishReasonStr != null) {
        return FinishReason.fromString(finishReasonStr);
      }
    }
    return FinishReason.STOP;
  }

  private TokenUsage calculateTokenUsage(org.springframework.ai.chat.model.ChatResponse response) {
    int promptTokens = 0;
    int completionTokens = 0;
    int totalTokens = 0;

    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
      Usage usage = response.getMetadata().getUsage();
      promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
      completionTokens =
          usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0;
      totalTokens =
          usage.getTotalTokens() != null
              ? usage.getTotalTokens().intValue()
              : (promptTokens + completionTokens);
    }

    return new TokenUsage(promptTokens, completionTokens, totalTokens);
  }

  private boolean isRetryable(Throwable ex) {
    if (ex == null) {
      return false;
    }
    String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
    return msg.contains(KEYWORD_TIMEOUT)
        || msg.contains(KEYWORD_CONNECTION)
        || msg.contains(KEYWORD_500)
        || msg.contains(KEYWORD_502)
        || msg.contains(KEYWORD_503)
        || msg.contains(KEYWORD_504)
        || msg.contains(KEYWORD_RESET);
  }

  private void recordAudit(
      String sessionId,
      String provider,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long durationMs,
      boolean success,
      String errorMessage) {
    llmCallRepository.ifPresent(
        repo -> {
          try {
            String activeSessionId = sessionId;
            if (activeSessionId == null || activeSessionId.isBlank()) {
              activeSessionId = MDC.get(DEFAULT_SESSION_KEY);
            }
            if (activeSessionId == null || activeSessionId.isBlank()) {
              activeSessionId = UNASSIGNED_SESSION;
            }

            LlmCallEntity entity =
                new LlmCallEntity(
                    UUID.randomUUID().toString(),
                    activeSessionId,
                    provider,
                    model,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    durationMs,
                    success,
                    errorMessage,
                    Instant.now());

            repo.save(entity);
            if (log.isDebugEnabled()) {
              log.debug(
                  "LlmCallAudit recorded: id={}, provider={}, model={}, success={}, totalTokens={}",
                  entity.getId(),
                  provider,
                  model,
                  success,
                  totalTokens);
            }
          } catch (Exception ex) {
            log.error("Failed to persist LlmCallAudit: {}", ex.getMessage(), ex);
          }
        });
  }
}
