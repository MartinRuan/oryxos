package com.oryxos.provider.adapter;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.mock.MockChatModel;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

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
  private static final String DEFAULT_MOCK_KEY = "mock-key";
  private static final String DEFAULT_QWEN_MODEL = "qwen-plus";

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

    DashScopeApi dashScopeApi;
    if (baseUrl != null && !baseUrl.isBlank()) {
      dashScopeApi = new DashScopeApi(apiKey, baseUrl);
    } else {
      dashScopeApi = new DashScopeApi(apiKey);
    }

    DashScopeChatOptions options = DashScopeChatOptions.builder().withModel(defaultModel).build();

    log.info(
        "Creating DashScope/OpenAI-compatible ChatModel for provider: {}, model: {}, baseUrl: {}",
        providerName,
        defaultModel,
        baseUrl != null ? baseUrl : "default");

    return new DashScopeChatModel(dashScopeApi, options);
  }
}
