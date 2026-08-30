package com.oryxos.provider.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.ProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * ProviderAutoConfiguration 自动装配测试.
 *
 * @author oryxos
 */
class ProviderAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ProviderAutoConfiguration.class));

  @Test
  @DisplayName("验证 ProviderRegistry 和 ProviderService 自动装配成功并内置 mock Provider")
  void testAutoConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ProviderRegistry.class);
          assertThat(context).hasSingleBean(ProviderService.class);

          ProviderRegistry registry = context.getBean(ProviderRegistry.class);
          assertThat(registry.isAvailable("mock")).isTrue();
          assertThat(registry.getDescriptor("mock")).isPresent();
        });
  }
}
