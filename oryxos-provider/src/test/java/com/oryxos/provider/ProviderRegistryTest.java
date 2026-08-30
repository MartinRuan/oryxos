package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.mock.MockChatModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProviderRegistry 显式注册与按名寻址单元测试.
 *
 * @author oryxos
 */
class ProviderRegistryTest {

  private ProviderRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ProviderRegistry();
  }

  @Test
  @DisplayName("验证多 Provider 显式注册与按名寻址准确率 100%")
  void testRegisterAndRetrieve() {
    ProviderDescriptor qwen =
        ProviderDescriptor.builder()
            .name("qwen")
            .type("CLOUD")
            .defaultModel("qwen-plus")
            .supportedModels(List.of("qwen-plus", "qwen-max"))
            .build();

    ProviderDescriptor deepseek =
        ProviderDescriptor.builder()
            .name("deepseek")
            .type("CLOUD")
            .defaultModel("deepseek-chat")
            .build();

    registry.register(qwen, new MockChatModel("Qwen reply"));
    registry.register(deepseek, new MockChatModel("DeepSeek reply"));

    assertThat(registry.isAvailable("qwen")).isTrue();
    assertThat(registry.isAvailable("deepseek")).isTrue();
    assertThat(registry.isAvailable("unknown")).isFalse();

    assertThat(registry.getDescriptor("qwen")).isPresent();
    assertThat(registry.getDescriptor("qwen").get().getDefaultModel()).isEqualTo("qwen-plus");

    assertThat(registry.getDescriptor("deepseek")).isPresent();
    assertThat(registry.getDescriptor("deepseek").get().getDefaultModel())
        .isEqualTo("deepseek-chat");

    assertThat(registry.listDescriptors()).hasSize(2);
  }

  @Test
  @DisplayName("验证 Provider 注销机制")
  void testUnregister() {
    ProviderDescriptor mock =
        ProviderDescriptor.builder().name("mock").type("MOCK").defaultModel("mock-model").build();

    registry.register(mock, new MockChatModel());
    assertThat(registry.isAvailable("mock")).isTrue();

    registry.unregister("mock");
    assertThat(registry.isAvailable("mock")).isFalse();
    assertThat(registry.getDescriptor("mock")).isEmpty();
  }
}
