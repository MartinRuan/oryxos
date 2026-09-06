package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件沙箱白名单配置属性绑定.
 *
 * @param allowedPaths 允许读写的根路径列表（相对或绝对路径）
 * @author OryxOS Team
 */
@ConfigurationProperties(prefix = "file")
public record FileSandboxProperties(List<String> allowedPaths) {

  /**
   * 规范化构造器，确保非空.
   *
   * @param allowedPaths 允许路径列表
   */
  public FileSandboxProperties {
    if (allowedPaths == null) {
      allowedPaths = List.of();
    }
  }
}
