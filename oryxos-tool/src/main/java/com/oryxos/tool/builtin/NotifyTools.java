package com.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.NotifyTarget;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import org.springframework.stereotype.Component;

/**
 * 内置出站通知工具.
 *
 * <p>供 Agent 运行时主动向配置好的渠道（如群机器人 Webhook）推送消息.
 *
 * @author OryxOS Team
 */
@Component
public class NotifyTools implements OryxTool {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_CHANNEL = "channel";
  private static final String CONFIG_KEY_URL = "url";

  private final Sandbox sandbox;
  private final NotifyChannelAdapter adapter;
  private final ProfileContext profileContext;

  /**
   * 构造 NotifyTools.
   *
   * @param sandbox 沙箱安全检查器
   * @param adapter 通知渠道适配器
   * @param profileContext Profile 上下文容器
   */
  public NotifyTools(Sandbox sandbox, NotifyChannelAdapter adapter, ProfileContext profileContext) {
    this.sandbox = sandbox;
    this.adapter = adapter;
    this.profileContext = profileContext;
  }

  @Override
  public String getName() {
    return "notify";
  }

  @Override
  public String getDescription() {
    return "把一条消息推送到当前 Agent 配置好的通知渠道（如钉钉、飞书、企业微信群机器人）";
  }

  @Override
  public String getInputSchema() {
    return "{\"type\":\"object\",\"properties\":{"
        + "\"content\":{\"type\":\"string\",\"description\":\"待推送的消息内容\"},"
        + "\"channel\":{\"type\":\"string\",\"description\":\"可选通知渠道名称，缺省时使用首个配置的渠道\"}},"
        + "\"required\":[\"content\"]}";
  }

  @Override
  public ToolResult execute(String inputJson) {
    String content = "";
    String channel = null;
    if (inputJson != null && !inputJson.isBlank()) {
      try {
        JsonNode node = OBJECT_MAPPER.readTree(inputJson);
        if (node.has(FIELD_CONTENT)) {
          content = node.get(FIELD_CONTENT).asText();
        }
        if (node.has(FIELD_CHANNEL)) {
          channel = node.get(FIELD_CHANNEL).asText();
        }
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        content = inputJson;
      }
    }
    return notify(content, channel);
  }

  /**
   * 把一条消息推送到当前 Agent 配置好的通知渠道.
   *
   * @param content 待推送的消息内容
   * @param channel 可选通知渠道名称（缺省时使用首个配置的渠道）
   * @return 工具执行结果
   */
  public ToolResult notify(String content, String channel) {
    Profile.NotifyChannelConfig channelConfig = profileContext.resolveNotifyChannel(channel);
    NotifyTarget target = new NotifyTarget(channelConfig.getType(), channelConfig.getConfig());
    sandbox.enforce(
        new SandboxAction(ActionType.HTTP_REQUEST, target.config().get(CONFIG_KEY_URL)));
    adapter.send(target, content);
    return ToolResult.success("已推送");
  }
}
