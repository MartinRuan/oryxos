package com.oryxos.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.ProviderService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
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

  @Test
  @DisplayName("配置的 Provider 超时会传递到底层同步 HTTP 客户端")
  void configuredProviderTimeoutIsAppliedToHttpClient() throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            Thread.sleep(5_000L);
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } catch (IOException ignored) {
            // 客户端按期超时后关闭连接是本用例的预期结果。
          } finally {
            exchange.close();
          }
        });
    server.start();

    try {
      String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      contextRunner
          .withPropertyValues(
              "oryxos.default-timeout-seconds=1",
              "oryxos.providers.slow.type=CLOUD",
              "oryxos.providers.slow.default-model=slow-model",
              "oryxos.providers.slow.base-url=" + baseUrl,
              "oryxos.providers.slow.api-key=test-key")
          .run(
              context -> {
                ProviderRegistry registry = context.getBean(ProviderRegistry.class);
                ChatModel model = registry.getModel("slow").orElseThrow();
                long startedAt = System.nanoTime();

                assertThatThrownBy(() -> model.call("ping")).isInstanceOf(RuntimeException.class);
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                assertThat(elapsedMillis).isLessThan(4_000L);
              });
    } finally {
      server.stop(0);
    }
  }
}
