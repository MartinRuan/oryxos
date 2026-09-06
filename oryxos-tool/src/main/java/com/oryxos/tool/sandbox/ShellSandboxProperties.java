package com.oryxos.tool.sandbox;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shell 沙箱命令白名单配置属性绑定.
 *
 * @param allowedCommands 允许执行的命令集合（按首个可执行文件 token 比对）
 * @author OryxOS Team
 */
@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {

  /**
   * 规范化构造器，确保非空.
   *
   * @param allowedCommands 允许命令列表
   */
  public ShellSandboxProperties {
    if (allowedCommands == null) {
      allowedCommands = List.of();
    }
  }
}
