package com.oryxos.tool.config;

import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.notify.WebhookNotifyAdapter;
import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OryxOS Tool 模块自动装配配置类.
 *
 * @author OryxOS Team
 */
@AutoConfiguration
@EnableConfigurationProperties({
  FileSandboxProperties.class,
  ShellSandboxProperties.class,
  HttpSandboxProperties.class
})
public class ToolAutoConfiguration {

  private static final Duration WEBHOOK_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration WEBHOOK_READ_TIMEOUT = Duration.ofSeconds(10);

  /**
   * 注册缺省 RestClient.Builder.
   *
   * @return RestClient 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public RestClient restClient() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(WEBHOOK_CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(WEBHOOK_READ_TIMEOUT);
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  /**
   * 注册应用层白名单安全沙箱.
   *
   * @param fileProps 文件路径白名单配置
   * @param shellProps Shell 命令白名单配置
   * @param httpProps HTTP 域名白名单配置
   * @return Sandbox 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public Sandbox sandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    return new WhitelistSandbox(fileProps, shellProps, httpProps);
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
   * 注册缺省 FileTools.
   *
   * @param sandbox 沙箱检查器
   * @return FileTools 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public FileTools fileTools(Sandbox sandbox) {
    return new FileTools(sandbox);
  }

  /**
   * 注册缺省 ShellTools.
   *
   * @param sandbox 沙箱检查器
   * @return ShellTools 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ShellTools shellTools(Sandbox sandbox) {
    return new ShellTools(sandbox);
  }

  /**
   * 注册缺省 HttpTools.
   *
   * @param sandbox 沙箱检查器
   * @return HttpTools 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public HttpTools httpTools(Sandbox sandbox) {
    return new HttpTools(sandbox);
  }

  /**
   * 将 read_file 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param fileTools 文件工具集
   * @return read_file 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "readFileTool")
  public OryxTool readFileTool(FileTools fileTools) {
    return fileTools.getReadFileTool();
  }

  /**
   * 将 write_file 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param fileTools 文件工具集
   * @return write_file 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "writeFileTool")
  public OryxTool writeFileTool(FileTools fileTools) {
    return fileTools.getWriteFileTool();
  }

  /**
   * 将 list_dir 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param fileTools 文件工具集
   * @return list_dir 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "listDirTool")
  public OryxTool listDirTool(FileTools fileTools) {
    return fileTools.getListDirTool();
  }

  /**
   * 将 http_get 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param httpTools HTTP 工具集
   * @return http_get 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "httpGetTool")
  public OryxTool httpGetTool(HttpTools httpTools) {
    return httpTools.getHttpGetTool();
  }

  /**
   * 将 http_post 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param httpTools HTTP 工具集
   * @return http_post 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "httpPostTool")
  public OryxTool httpPostTool(HttpTools httpTools) {
    return httpTools.getHttpPostTool();
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
   * 注册缺省 ToolRegistry 并自动装载全部内置工具.
   *
   * @param fileTools 内置文件工具组件
   * @param shellTools 内置命令工具组件
   * @param httpTools 内置 HTTP 工具组件
   * @param notifyTools 内置通知工具组件
   * @param otherTools 其它已注册的 OryxTool 集合
   * @return ToolRegistry 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ToolRegistry toolRegistry(
      FileTools fileTools,
      ShellTools shellTools,
      HttpTools httpTools,
      NotifyTools notifyTools,
      List<OryxTool> otherTools) {
    ToolRegistry registry = new ToolRegistry();
    if (fileTools != null) {
      for (OryxTool tool : fileTools.getTools()) {
        registry.register(tool);
      }
    }
    if (shellTools != null) {
      registry.register(shellTools);
    }
    if (httpTools != null) {
      for (OryxTool tool : httpTools.getTools()) {
        registry.register(tool);
      }
    }
    if (notifyTools != null) {
      registry.register(notifyTools);
    }
    if (otherTools != null) {
      for (OryxTool tool : otherTools) {
        registry.register(tool);
      }
    }
    return registry;
  }
}
