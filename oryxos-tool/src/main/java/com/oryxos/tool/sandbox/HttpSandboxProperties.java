package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 出站沙箱域名白名单配置属性绑定.
 *
 * @param allowedDomains 允许连接的域名列表（支持精确匹配及 *.domain.com 通配符）
 * @author OryxOS Team
 */
@ConfigurationProperties(prefix = "http")
public record HttpSandboxProperties(List<String> allowedDomains) {

  /**
   * 规范化构造器，确保非空.
   *
   * @param allowedDomains 允许域名列表
   */
  public HttpSandboxProperties {
    if (allowedDomains == null) {
      allowedDomains = List.of();
    }
  }
}
