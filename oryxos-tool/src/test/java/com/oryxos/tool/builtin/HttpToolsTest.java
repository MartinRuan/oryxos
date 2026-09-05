package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HttpTools 单元与契约测试.
 *
 * @author OryxOS Team
 */
class HttpToolsTest {

  private HttpServer localServer;
  private int serverPort;
  private Sandbox sandbox;
  private HttpTools httpTools;

  @BeforeEach
  void setUp() throws Exception {
    sandbox = mock(Sandbox.class);
    httpTools = new HttpTools(sandbox);

    localServer = HttpServer.create(new InetSocketAddress(0), 0);
    serverPort = localServer.getAddress().getPort();
    localServer.createContext(
        "/weather",
        exchange -> {
          byte[] response =
              "{\"temp\":25,\"condition\":\"Sunny\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
          }
        });
    localServer.createContext(
        "/echo",
        exchange -> {
          byte[] requestBody = exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, requestBody.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(requestBody);
          }
        });
    localServer.start();
  }

  @AfterEach
  void tearDown() {
    if (localServer != null) {
      localServer.stop(0);
    }
  }

  @Test
  @DisplayName("http_get 正常发起请求并返回响应内容")
  void http_get_应能取回响应() {
    OryxTool getTool = httpTools.getHttpGetTool();
    String url = "http://localhost:" + serverPort + "/weather";
    String inputJson = "{\"url\":\"" + url + "\"}";

    ToolResult result = getTool.execute(inputJson);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).contains("Sunny");
    verify(sandbox).enforce(any());
  }

  @Test
  @DisplayName("http_get 命中白名单外域名应被拦下")
  void http_get_命中白名单外域名应被拦下() {
    doThrow(new RuntimeException("Sandbox violation: domain not allowed"))
        .when(sandbox)
        .enforce(any());

    OryxTool getTool = httpTools.getHttpGetTool();
    String inputJson = "{\"url\":\"https://evil.example.com/api\"}";

    assertThatThrownBy(() -> getTool.execute(inputJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");
  }

  @Test
  @DisplayName("http_post 正常发起请求并携带请求体")
  void http_post_应能提交并取回响应() {
    OryxTool postTool = httpTools.getHttpPostTool();
    String url = "http://localhost:" + serverPort + "/echo";
    String inputJson = "{\"url\":\"" + url + "\",\"body\":\"{\\\"message\\\":\\\"ping\\\"}\"}";

    ToolResult result = postTool.execute(inputJson);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).contains("ping");
    verify(sandbox).enforce(any());
  }

  @Test
  @DisplayName("http_post 命中白名单外域名应被拦下")
  void http_post_命中白名单外域名应被拦下() {
    doThrow(new RuntimeException("Sandbox violation: domain not allowed"))
        .when(sandbox)
        .enforce(any());

    OryxTool postTool = httpTools.getHttpPostTool();
    String inputJson = "{\"url\":\"https://unauthorized.target.com/upload\",\"body\":\"data\"}";

    assertThatThrownBy(() -> postTool.execute(inputJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox violation");
  }
}
