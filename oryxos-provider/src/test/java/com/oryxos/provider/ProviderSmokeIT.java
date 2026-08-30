package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.config.ProviderProperties;
import com.oryxos.provider.impl.ProviderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Provider 真实连通性冒烟集成测试. 需配置环境变量 DEEPSEEK_API_KEY 或 QWEN_API_KEY 时才实际向云端发起调用，否则自动跳过.
 *
 * @author oryxos
 */
@Tag("integration")
class ProviderSmokeIT {

  @Test
  @DisplayName("真实 DeepSeek API 连通性冒烟调用")
  void liveProviderSmokeTest() {
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeTrue(
        apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder"),
        "DEEPSEEK_API_KEY 未配置有效密钥，跳过集成测试");

    ProviderRegistry registry = new ProviderRegistry();
    ProviderDescriptor descriptor =
        ProviderDescriptor.builder()
            .name("deepseek")
            .apiKey(apiKey)
            .baseUrl("https://api.deepseek.com")
            .defaultModel("deepseek-chat")
            .build();

    ProviderService service = new ProviderServiceImpl(registry, new ProviderProperties(), null);
    service.registerProvider(descriptor);

    Profile profile = new Profile();
    profile.setName("smoke-agent");
    profile.setProvider(new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.7));

    ChatResponse response =
        service.chat("smoke-session", profile, ChatRequest.builder().prompt("Say 1+1=").build());

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotBlank();
    assertThat(response.getProvider()).isEqualTo("deepseek");
  }
}
