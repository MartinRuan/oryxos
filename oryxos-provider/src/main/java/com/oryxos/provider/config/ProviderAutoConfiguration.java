package com.oryxos.provider.config;

import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.ProviderService;
import com.oryxos.provider.adapter.ChatModelFactory;
import com.oryxos.provider.impl.ProviderServiceImpl;
import com.oryxos.provider.mock.MockChatModel;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provider 模块 Spring Boot 自动配置类. 负责扫描并装配 ProviderProperties 中声明的各模型提供商，建立显式映射表.
 *
 * @author oryxos
 */
@AutoConfiguration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ProviderAutoConfiguration.class);

  /**
   * 初始化并注册 ProviderRegistry 实例.
   *
   * @param properties Provider 配置属性
   * @return ProviderRegistry Bean
   */
  @Bean
  @ConditionalOnMissingBean
  public ProviderRegistry providerRegistry(ProviderProperties properties) {
    ProviderRegistry registry = new ProviderRegistry();

    // 默认内置注册 mock Provider（保证离线测试与 CI 100% 可用）
    ProviderDescriptor mockDescriptor =
        ProviderDescriptor.builder().name("mock").type("MOCK").defaultModel("mock-model").build();
    registry.register(mockDescriptor, new MockChatModel());

    // 显式装配 YAML 配置中声明的所有 Provider
    if (!properties.getProviders().isEmpty()) {
      for (Map.Entry<String, ProviderProperties.ProviderConfig> entry :
          properties.getProviders().entrySet()) {
        String providerName = entry.getKey();
        ProviderProperties.ProviderConfig config = entry.getValue();

        ProviderDescriptor descriptor =
            ProviderDescriptor.builder()
                .name(providerName)
                .type(config.getType())
                .defaultModel(config.getDefaultModel())
                .supportedModels(config.getSupportedModels())
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        try {
          ChatModel chatModel = ChatModelFactory.createChatModel(descriptor);
          registry.register(descriptor, chatModel);
        } catch (Exception ex) {
          log.warn(
              "Failed to initialize ChatModel for provider: {}, will skip auto-registration: {}",
              providerName,
              ex.getMessage());
        }
      }
    }

    return registry;
  }

  /**
   * 初始化统一门面服务 ProviderService 实例.
   *
   * @param providerRegistry Provider 显式注册中心
   * @param providerProperties Provider 配置属性
   * @param llmCallRepository 审计仓储（可选）
   * @return ProviderService Bean
   */
  @Bean
  @ConditionalOnMissingBean
  public ProviderService providerService(
      ProviderRegistry providerRegistry,
      ProviderProperties providerProperties,
      @Autowired(required = false) LlmCallRepository llmCallRepository) {
    return new ProviderServiceImpl(providerRegistry, providerProperties, llmCallRepository);
  }
}
