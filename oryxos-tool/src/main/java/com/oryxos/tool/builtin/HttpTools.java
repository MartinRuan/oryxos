package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 内置 HTTP 网络请求工具集（包含 http_get 与 http_post）.
 *
 * <p>所有出站网络请求必须在发起实际网络连接前，首行调用 Sandbox.enforce 进行白名单与 SSRF 安全检查.
 *
 * @author OryxOS Team
 */
@Component
public class HttpTools {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int HTTP_STATUS_OK_MIN = 200;
  private static final int HTTP_STATUS_OK_MAX = 300;
  private static final int HTTP_STATUS_SERVER_ERROR_MIN = 500;
  private static final String PARAM_URL = "url";
  private static final String PARAM_BODY = "body";
  private static final String HTTP_GET_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
          + "\"url\":{\"type\":\"string\",\"description\":\"要请求的完整 URL\"}},"
          + "\"required\":[\"url\"]}";
  private static final String HTTP_POST_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
          + "\"url\":{\"type\":\"string\",\"description\":\"要请求的完整 URL\"},"
          + "\"body\":{\"type\":\"string\",\"description\":\"POST 请求体内容\"}},"
          + "\"required\":[\"url\"]}";

  private final Sandbox sandbox;
  private final HttpClient httpClient;
  private final OryxTool httpGetTool;
  private final OryxTool httpPostTool;

  /**
   * 构造 HttpTools.
   *
   * @param sandbox 沙箱安全检查器
   */
  public HttpTools(Sandbox sandbox) {
    this.sandbox = sandbox;
    this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    this.httpGetTool = new HttpGetTool();
    this.httpPostTool = new HttpPostTool();
  }

  /**
   * 获取 HTTP GET 工具.
   *
   * @return http_get 工具
   */
  public OryxTool getHttpGetTool() {
    return httpGetTool;
  }

  /**
   * 获取 HTTP POST 工具.
   *
   * @return http_post 工具
   */
  public OryxTool getHttpPostTool() {
    return httpPostTool;
  }

  /**
   * 获取本组件提供的全部工具实例.
   *
   * @return 工具列表
   */
  public List<OryxTool> getTools() {
    return List.of(httpGetTool, httpPostTool);
  }

  private class HttpGetTool implements OryxTool {
    @Override
    public String getName() {
      return "http_get";
    }

    @Override
    public String getDescription() {
      return "发起一个 HTTP GET 请求，返回响应体";
    }

    @Override
    public String getInputSchema() {
      return HTTP_GET_SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
      String url = parseUrl(inputJson);

      // 首行强制沙箱检查
      sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));

      try {
        HttpRequest request =
            HttpRequest.newBuilder().uri(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= HTTP_STATUS_OK_MIN
            && response.statusCode() < HTTP_STATUS_OK_MAX) {
          return ToolResult.success(response.body());
        } else {
          String msg =
              "HTTP GET " + url + " status " + response.statusCode() + ": " + response.body();
          return ToolResult.failure(msg, response.statusCode() >= HTTP_STATUS_SERVER_ERROR_MIN);
        }
      } catch (IOException e) {
        return ToolResult.failure("HTTP GET failed for " + url + ": " + e.getMessage(), true);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return ToolResult.failure("HTTP GET request interrupted: " + e.getMessage(), false);
      }
    }
  }

  private final class HttpPostTool implements OryxTool {
    @Override
    public String getName() {
      return "http_post";
    }

    @Override
    public String getDescription() {
      return "向指定 URL 发起 HTTP POST 请求并返回响应正文";
    }

    @Override
    public String getInputSchema() {
      return HTTP_POST_SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
      String url = parseUrl(inputJson);
      String body = parseBody(inputJson);

      // 首行强制沙箱检查
      sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url));

      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= HTTP_STATUS_OK_MIN
            && response.statusCode() < HTTP_STATUS_OK_MAX) {
          return ToolResult.success(response.body());
        } else {
          String msg =
              "HTTP POST " + url + " status " + response.statusCode() + ": " + response.body();
          return ToolResult.failure(msg, response.statusCode() >= HTTP_STATUS_SERVER_ERROR_MIN);
        }
      } catch (IOException e) {
        return ToolResult.failure("HTTP POST failed for " + url + ": " + e.getMessage(), true);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return ToolResult.failure("HTTP POST request interrupted: " + e.getMessage(), false);
      }
    }
  }

  private static String parseUrl(String inputJson) {
    if (inputJson == null || inputJson.isBlank()) {
      return "";
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(inputJson);
      if (node.has(PARAM_URL)) {
        return node.get(PARAM_URL).asText();
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // ignore
    }
    return inputJson.trim();
  }

  private static String parseBody(String inputJson) {
    if (inputJson == null || inputJson.isBlank()) {
      return "";
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(inputJson);
      if (node.has(PARAM_BODY)) {
        return node.get(PARAM_BODY).asText();
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // ignore
    }
    return "";
  }
}
