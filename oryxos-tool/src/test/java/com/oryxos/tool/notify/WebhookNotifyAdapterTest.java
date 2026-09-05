package com.oryxos.tool.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.core.exception.OryxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * WebhookNotifyAdapter 单元测试.
 *
 * @author OryxOS Team
 */
class WebhookNotifyAdapterTest {

  private HttpServer server;
  private int serverPort;
  private WebhookNotifyAdapter adapter;
  private final AtomicReference<String> receivedBody = new AtomicReference<>();
  private final AtomicReference<String> receivedMethod = new AtomicReference<>();
  private final AtomicReference<String> receivedContentType = new AtomicReference<>();
  private final AtomicInteger responseCode = new AtomicInteger(200);

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    serverPort = server.getAddress().getPort();
    server.createContext(
        "/webhook",
        new HttpHandler() {
          @Override
          public void handle(HttpExchange exchange) throws IOException {
            receivedMethod.set(exchange.getRequestMethod());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try (InputStream is = exchange.getRequestBody()) {
              receivedBody.set(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            int status = responseCode.get();
            exchange.sendResponseHeaders(status, 0);
            exchange.close();
          }
        });
    server.start();

    adapter = new WebhookNotifyAdapter(RestClient.builder().build());
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("发送消息成功：正确发送 POST 请求，带 content 载荷且 URL 来源于配置")
  void 发送消息_成功构造POST请求并包含content载荷() {
    String webhookUrl = "http://localhost:" + serverPort + "/webhook";
    NotifyTarget target = new NotifyTarget("webhook", Map.of("url", webhookUrl));

    adapter.send(target, "今日天气晴朗，气温 25℃");

    assertThat(receivedMethod.get()).isEqualTo("POST");
    assertThat(receivedContentType.get()).contains("application/json");
    assertThat(receivedBody.get()).contains("\"content\"");
    assertThat(receivedBody.get()).contains("今日天气晴朗，气温 25℃");
  }

  @Test
  @DisplayName("远端返回 5xx 错误时，异常必须向上抛出且不被静默吞掉")
  void 远端返回5xx错误_异常必须向上抛出不静默吞掉() {
    responseCode.set(500);
    String webhookUrl = "http://localhost:" + serverPort + "/webhook";
    NotifyTarget target = new NotifyTarget("webhook", Map.of("url", webhookUrl));

    assertThatThrownBy(() -> adapter.send(target, "测试故障消息"))
        .isInstanceOf(RestClientResponseException.class);
  }

  @Test
  @DisplayName("缺少 URL 配置时明确抛出参数异常")
  void 缺少URL配置_抛出参数异常() {
    NotifyTarget targetWithoutUrl = new NotifyTarget("webhook", Map.of());

    assertThatThrownBy(() -> adapter.send(targetWithoutUrl, "消息"))
        .isInstanceOf(OryxException.class);
  }

  @Test
  @DisplayName("NotifyTarget 为空时明确抛出参数异常")
  void target为空_抛出参数异常() {
    assertThatThrownBy(() -> adapter.send(null, "消息")).isInstanceOf(OryxException.class);
  }
}
