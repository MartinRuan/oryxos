package com.oryxos.tool.config;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.WebhookNotifyAdapter;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxAction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * OryxOS Tool 模块自动装配配置类.
 *
 * @author OryxOS Team
 */
@AutoConfiguration
public class ToolAutoConfiguration {

  /**
   * 注册缺省 RestClient.Builder.
   *
   * @return RestClient 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public RestClient restClient() {
    return RestClient.builder().build();
  }

  /**
   * 注册缺省沙箱安全检查器（核心阶段占位，24 节由 WhitelistSandbox 完整承接）.
   *
   * @return Sandbox 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public Sandbox sandbox() {
    return new DefaultSandbox();
  }

  /**
   * 注册缺省 Webhook 通知适配器.
   *
   * @param restClient RestClient 客户端
   * @return WebhookNotifyAdapter 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public WebhookNotifyAdapter webhookNotifyAdapter(RestClient restClient) {
    return new WebhookNotifyAdapter(restClient);
  }

  /**
   * 注册缺省 NotifyTools.
   *
   * @param sandbox 沙箱检查器
   * @param adapter 通知适配器
   * @param profileContext Profile 上下文
   * @return NotifyTools 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public NotifyTools notifyTools(
      Sandbox sandbox, NotifyChannelAdapter adapter, ProfileContext profileContext) {
    return new NotifyTools(sandbox, adapter, profileContext);
  }

  /**
   * 注册缺省 ToolRegistry.
   *
   * @return ToolRegistry 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ToolRegistry toolRegistry() {
    return new ToolRegistry();
  }

  /** 缺省放行沙箱实现（占位用途，24 节由 WhitelistSandbox 完整承接）. */
  public static class DefaultSandbox implements Sandbox {

    @Override
    public boolean check(String target) {
      return true;
    }

    @Override
    public void enforce(SandboxAction action) {
      // 核心占位放行，24 节接驳 WhitelistSandbox 白名单校验
    }
  }
}
