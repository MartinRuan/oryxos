package com.oryxos.tool.notify;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 HTTP Webhook 出站通知适配器.
 *
 * <p>以 JSON 格式发送 POST 请求至指定 Webhook URL，并根据渠道类型自适应组装钉钉、飞书、企业微信等标准 Payload.
 *
 * @author OryxOS Team
 */
@Component
public class WebhookNotifyAdapter implements NotifyChannelAdapter {

  private static final String CONFIG_KEY_URL = "url";
  private static final String MSG_TYPE_TEXT = "text";
  private static final String FIELD_MSGTYPE = "msgtype";
  private static final String FIELD_MSG_TYPE = "msg_type";
  private static final String FIELD_TEXT = "text";
  private static final String FIELD_CONTENT = "content";

  private static final String TYPE_DINGTALK = "dingtalk";
  private static final String HOST_DINGTALK = "dingtalk.com";
  private static final String TYPE_FEISHU = "feishu";
  private static final String TYPE_LARK = "lark";
  private static final String HOST_FEISHU = "feishu.cn";
  private static final String TYPE_WECOM = "wecom";
  private static final String TYPE_WEIXIN = "weixin";
  private static final String HOST_WEIXIN = "weixin.qq.com";

  private final RestClient restClient;

  /**
   * 使用自定义 RestClient 构造适配器.
   *
   * @param restClient RestClient 实例
   */
  public WebhookNotifyAdapter(RestClient restClient) {
    this.restClient = restClient != null ? restClient : RestClient.builder().build();
  }

  /** 默认无参构造器，使用默认 RestClient. */
  public WebhookNotifyAdapter() {
    this(RestClient.builder().build());
  }

  @Override
  public void send(NotifyTarget target, String content) {
    if (target == null || target.config() == null) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "NotifyTarget and its config must not be null");
    }
    String url = target.config().get(CONFIG_KEY_URL);
    if (url == null || url.trim().isEmpty()) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Webhook URL must not be null or empty in config");
    }

    String messageContent = content != null ? content : "";
    Object payload = buildPayload(target, url, messageContent);

    restClient
        .post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }

  private Object buildPayload(NotifyTarget target, String url, String messageContent) {
    String type =
        target.channelType() != null ? target.channelType().trim().toLowerCase(Locale.ROOT) : "";
    String lowerUrl = url.toLowerCase(Locale.ROOT);

    if (TYPE_DINGTALK.equals(type) || lowerUrl.contains(HOST_DINGTALK)) {
      return Map.of(
          FIELD_MSGTYPE, MSG_TYPE_TEXT, FIELD_TEXT, Map.of(FIELD_CONTENT, messageContent));
    }
    if (TYPE_FEISHU.equals(type) || TYPE_LARK.equals(type) || lowerUrl.contains(HOST_FEISHU)) {
      return Map.of(
          FIELD_MSG_TYPE, MSG_TYPE_TEXT, FIELD_CONTENT, Map.of(FIELD_TEXT, messageContent));
    }
    if (TYPE_WECOM.equals(type) || TYPE_WEIXIN.equals(type) || lowerUrl.contains(HOST_WEIXIN)) {
      return Map.of(
          FIELD_MSGTYPE, MSG_TYPE_TEXT, FIELD_TEXT, Map.of(FIELD_CONTENT, messageContent));
    }
    return Map.of(FIELD_CONTENT, messageContent);
  }
}
