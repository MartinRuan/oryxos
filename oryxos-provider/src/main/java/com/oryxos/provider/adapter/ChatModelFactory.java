package com.oryxos.provider.adapter;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.mock.MockChatModel;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * ChatModel 实例创建工厂. 负责根据 ProviderDescriptor 属性动态构建底层 Spring AI ChatModel 实例.
 *
 * @author oryxos
 */
public final class ChatModelFactory {

  private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

  private static final String TYPE_CLOUD = "CLOUD";
  private static final String TYPE_MOCK = "MOCK";
  private static final String PROVIDER_NAME_MOCK = "mock";
  private static final String PROVIDER_NAME_QWEN = "qwen";
  private static final String PROVIDER_NAME_DASHSCOPE = "dashscope";
  private static final String DEFAULT_MOCK_KEY = "mock-key";
  private static final String DEFAULT_QWEN_MODEL = "qwen-plus";
  private static final String PATH_V1_TRAILING_SLASH = "/v1/";
  private static final String PATH_V1 = "/v1";
  private static final String DASHSCOPE_KEYWORD = "dashscope";
  private static final String DEFAULT_BASE_URL_LABEL = "default";

  private ChatModelFactory() {
    // Utility class
  }

  /**
   * 根据提供商描述符创建对应的 ChatModel 实例.
   *
   * @param descriptor 提供商描述符
   * @return 初始化的 ChatModel 实例
   */
  public static ChatModel createChatModel(ProviderDescriptor descriptor) {
    if (descriptor == null) {
      return new MockChatModel();
    }

    String type =
        descriptor.getType() != null
            ? descriptor.getType().trim().toUpperCase(Locale.ROOT)
            : TYPE_CLOUD;
    String providerName = descriptor.getName().trim().toLowerCase(Locale.ROOT);

    if (TYPE_MOCK.equals(type) || PROVIDER_NAME_MOCK.equalsIgnoreCase(providerName)) {
      log.info("Creating MockChatModel for provider: {}", providerName);
      return new MockChatModel();
    }

    String apiKey = descriptor.getApiKey() != null ? descriptor.getApiKey() : DEFAULT_MOCK_KEY;
    String baseUrl = descriptor.getBaseUrl();
    String defaultModel =
        descriptor.getDefaultModel() != null && !descriptor.getDefaultModel().isBlank()
            ? descriptor.getDefaultModel()
            : DEFAULT_QWEN_MODEL;

    // 通义千问 / DashScope 原生协议
    if (isDashScopeProvider(providerName, baseUrl)) {
      DashScopeApi dashScopeApi = createDashScopeApi(apiKey, baseUrl);
      DashScopeChatOptions options = DashScopeChatOptions.builder().withModel(defaultModel).build();
      log.info(
          "Creating DashScope ChatModel for provider: {}, model: {}", providerName, defaultModel);
      return new OryxDashScopeChatModel(dashScopeApi, options);
    }

    // Kimi / DeepSeek / Ollama / OpenAI 及其他 OpenAI 兼容协议
    OpenAiApi openAiApi = createOpenAiApi(apiKey, baseUrl);
    OpenAiChatOptions openAiOptions = OpenAiChatOptions.builder().withModel(defaultModel).build();
    log.info(
        "Creating OpenAI-compatible ChatModel for provider: {}, model: {}, baseUrl: {}",
        providerName,
        defaultModel,
        baseUrl != null ? baseUrl : DEFAULT_BASE_URL_LABEL);
    return new OryxOpenAiChatModel(openAiApi, openAiOptions);
  }

  private static boolean isDashScopeProvider(String providerName, String baseUrl) {
    boolean isQwenOrDashScope =
        PROVIDER_NAME_QWEN.equalsIgnoreCase(providerName)
            || PROVIDER_NAME_DASHSCOPE.equalsIgnoreCase(providerName);
    if (!isQwenOrDashScope) {
      return false;
    }
    return baseUrl == null || baseUrl.isBlank() || baseUrl.contains(DASHSCOPE_KEYWORD);
  }

  private static DashScopeApi createDashScopeApi(String apiKey, String baseUrl) {
    if (baseUrl != null && !baseUrl.isBlank()) {
      return new DashScopeApi(apiKey, baseUrl);
    }
    return new DashScopeApi(apiKey);
  }

  private static OpenAiApi createOpenAiApi(String apiKey, String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return new OpenAiApi(apiKey);
    }
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl.trim());
    return new OpenAiApi(normalizedBaseUrl, apiKey);
  }

  private static String normalizeBaseUrl(String url) {
    // Spring AI OpenAiApi 默认 completionsPath 为 "/v1/chat/completions"
    // 若用户传入的 baseUrl 以 /v1 或 /v1/ 结尾，则剥离末尾 /v1 以免拼接出重复的 /v1/v1/chat/completions (导致 404)
    if (url.endsWith(PATH_V1_TRAILING_SLASH)) {
      return url.substring(0, url.length() - PATH_V1_TRAILING_SLASH.length());
    }
    if (url.endsWith(PATH_V1)) {
      return url.substring(0, url.length() - PATH_V1.length());
    }
    return url;
  }

  /**
   * OryxOS 扩展的 OpenAiChatModel. 重写 isToolCall 返回 false，彻底阻止 Spring AI 内部工具调用循环， 严格贯彻 Constitution
   * 原则二（由 ReActLoop + ToolExecutor 接管调度与执行）.
   */
  public static class OryxOpenAiChatModel extends OpenAiChatModel {

    public OryxOpenAiChatModel(OpenAiApi openAiApi, OpenAiChatOptions defaultOptions) {
      super(openAiApi, defaultOptions);
    }

    @Override
    protected boolean isToolCall(ChatResponse chatResponse, Set<String> toolNames) {
      return false;
    }

    @Override
    protected boolean isToolCall(Generation generation, Set<String> toolNames) {
      return false;
    }
  }

  /** OryxOS 扩展的 DashScopeChatModel. 重写 isToolCall 返回 false，彻底阻止 Spring AI 内部工具调用循环. */
  public static class OryxDashScopeChatModel extends DashScopeChatModel {

    public OryxDashScopeChatModel(DashScopeApi dashScopeApi, DashScopeChatOptions defaultOptions) {
      super(dashScopeApi, defaultOptions);
    }

    @Override
    protected boolean isToolCall(ChatResponse chatResponse, Set<String> toolNames) {
      return false;
    }

    @Override
    protected boolean isToolCall(Generation generation, Set<String> toolNames) {
      return false;
    }
  }
}
