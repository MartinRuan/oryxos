package com.oryxos.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.exception.OryxException;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ToolResult;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.sandbox.ActionType;
import com.oryxos.tool.sandbox.Sandbox;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * NotifyTools 单元与回归测试（验证沙箱先行与渠道解析契约）.
 *
 * @author OryxOS Team
 */
class NotifyToolsTest {

  private Sandbox sandbox;
  private NotifyChannelAdapter adapter;
  private ProfileContext profileContext;
  private NotifyTools notifyTools;

  @BeforeEach
  void setUp() {
    sandbox = mock(Sandbox.class);
    adapter = mock(NotifyChannelAdapter.class);
    profileContext = new ProfileContext();
    notifyTools = new NotifyTools(sandbox, adapter, profileContext);
  }

  @AfterEach
  void tearDown() {
    ProfileContext.clear();
  }

  @Test
  @DisplayName("发送前必须先过白名单校验（InOrder 顺序钉死）")
  void 发送前必须先过白名单校验() {
    Profile profile =
        Profile.builder()
            .name("ops-agent")
            .notifyChannels(
                List.of(
                    new Profile.NotifyChannelConfig(
                        "default",
                        "webhook",
                        Map.of("url", "https://oapi.feishu.cn/open-apis/bot/v2/hook/123"))))
            .build();
    ProfileContext.set(profile);

    ToolResult result = notifyTools.notify("hello", "default");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getContent()).isEqualTo("已推送");

    InOrder inOrder = inOrder(sandbox, adapter);
    inOrder
        .verify(sandbox)
        .enforce(
            argThat(
                a ->
                    a.type() == ActionType.HTTP_REQUEST
                        && "https://oapi.feishu.cn/open-apis/bot/v2/hook/123".equals(a.target())));
    inOrder.verify(adapter).send(any(), eq("hello"));
  }

  @Test
  @DisplayName("notify_channels 未配置时明确报错，避免静默失败")
  void notify_channels未配置_明确报错() {
    Profile profile = Profile.builder().name("no-notify-agent").build();
    ProfileContext.set(profile);

    assertThatThrownBy(() -> notifyTools.notify("hello", null))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("No notify channels configured");
  }

  @Test
  @DisplayName("channel 参数缺省时自动使用首个配置的通知渠道")
  void channel参数缺省_取第一个渠道() {
    Profile profile =
        Profile.builder()
            .name("ops-agent")
            .notifyChannels(
                List.of(
                    new Profile.NotifyChannelConfig(
                        "primary-hook", "webhook", Map.of("url", "https://primary.hook/url")),
                    new Profile.NotifyChannelConfig(
                        "secondary-hook", "webhook", Map.of("url", "https://secondary.hook/url"))))
            .build();
    ProfileContext.set(profile);

    ToolResult result = notifyTools.notify("播报内容", null);

    assertThat(result.isSuccess()).isTrue();
    verify(adapter)
        .send(argThat(t -> "https://primary.hook/url".equals(t.config().get("url"))), eq("播报内容"));
  }

  @Test
  @DisplayName("沙箱白名单校验拒绝时中断执行，不调用适配器发送")
  void 白名单校验拒绝_不执行发送() {
    Profile profile =
        Profile.builder()
            .name("ops-agent")
            .notifyChannels(
                List.of(
                    new Profile.NotifyChannelConfig(
                        "default", "webhook", Map.of("url", "https://unauthorized.domain.com"))))
            .build();
    ProfileContext.set(profile);
    doThrow(new RuntimeException("Sandbox check failed: domain not in whitelist"))
        .when(sandbox)
        .enforce(any());

    assertThatThrownBy(() -> notifyTools.notify("hello", "default"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Sandbox check failed");

    verify(adapter, never()).send(any(), any());
  }
}
