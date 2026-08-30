package com.oryxos.provider.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProviderProperties 配置绑定单元测试.
 *
 * @author oryxos
 */
class ProviderPropertiesTest {

  @Test
  @DisplayName("验证多 Provider 配置绑定与默认超时重试参数")
  void testPropertiesBinding() {
    ProviderProperties properties = new ProviderProperties();
    properties.setDefaultTimeoutSeconds(90);
    properties.setDefaultMaxRetries(3);

    ProviderProperties.ProviderConfig config = new ProviderProperties.ProviderConfig();
    config.setType("CLOUD");
    config.setDefaultModel("deepseek-chat");
    config.setSupportedModels(List.of("deepseek-chat", "deepseek-reasoner"));
    config.setBaseUrl("https://api.deepseek.com/v1");
    config.setApiKey("test-api-key");

    properties.setProviders(Map.of("deepseek", config));

    assertThat(properties.getDefaultTimeoutSeconds()).isEqualTo(90);
    assertThat(properties.getDefaultMaxRetries()).isEqualTo(3);
    assertThat(properties.getProviders()).containsKey("deepseek");

    ProviderProperties.ProviderConfig deepseekConfig = properties.getProviders().get("deepseek");
    assertThat(deepseekConfig.getDefaultModel()).isEqualTo("deepseek-chat");
    assertThat(deepseekConfig.getSupportedModels()).contains("deepseek-reasoner");
    assertThat(deepseekConfig.getBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
    assertThat(deepseekConfig.getApiKey()).isEqualTo("test-api-key");
  }
}
