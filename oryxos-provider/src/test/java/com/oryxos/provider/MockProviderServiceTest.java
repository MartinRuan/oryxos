package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.FinishReason;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.config.ProviderProperties;
import com.oryxos.provider.exception.ProviderException;
import com.oryxos.provider.impl.ProviderServiceImpl;
import com.oryxos.provider.mock.MockChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MockProviderService 基础调用与异常处理单元测试.
 *
 * @author oryxos
 */
class MockProviderServiceTest {

  private ProviderRegistry registry;
  private ProviderServiceImpl service;

  @BeforeEach
  void setUp() {
    registry = new ProviderRegistry();
    ProviderProperties properties = new ProviderProperties();
    service = new ProviderServiceImpl(registry, properties, null);

    ProviderDescriptor mockDescriptor =
        ProviderDescriptor.builder().name("mock").type("MOCK").defaultModel("mock-model").build();
    registry.register(mockDescriptor, new MockChatModel("Custom mock text output"));
  }

  @Test
  @DisplayName("验证同步调用 Mock Provider 返回结果与 Token 统计")
  void testSuccessfulCall() {
    ChatRequest request =
        ChatRequest.builder()
            .provider("mock")
            .message(ChatMessage.user("Hello"))
            .sessionId("sess-1")
            .build();

    ChatResponse response = service.call(request);

    assertThat(response).isNotNull();
    assertThat(response.getProvider()).isEqualTo("mock");
    assertThat(response.getModel()).isEqualTo("mock-model");
    assertThat(response.getContent()).isEqualTo("Custom mock text output");
    assertThat(response.getFinishReason()).isEqualTo(FinishReason.STOP);
    assertThat(response.getUsage().getTotalTokens()).isEqualTo(30);
    assertThat(response.getDurationMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("验证请求未注册的 Provider 时准确抛出 PROVIDER_NOT_FOUND 异常")
  void testUnregisteredProviderThrows() {
    ChatRequest request =
        ChatRequest.builder()
            .provider("non-existent-provider")
            .message(ChatMessage.user("Hello"))
            .build();

    assertThatThrownBy(() -> service.call(request))
        .isInstanceOf(ProviderException.class)
        .hasMessageContaining("Provider not found in registry");
  }
}
