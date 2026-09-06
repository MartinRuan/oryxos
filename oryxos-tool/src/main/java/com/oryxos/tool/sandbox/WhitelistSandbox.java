package com.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 应用层白名单安全沙箱实现.
 *
 * <p>核心阶段落地第一档轻量级应用层防护：
 *
 * <ul>
 *   <li>文件路径：工作区路径规范化校验与相对路径穿越拦截
 *   <li>Shell 命令：首 Token 可执行文件白名单精确比对
 *   <li>HTTP 出站：精确域名及严密点号边界通配符（*.domain.com）比对
 *   <li>空配置全闭：配置为空时遵循全闭原则，拒绝一切未经许可的物理操作
 * </ul>
 *
 * @author OryxOS Team
 */
@Component
public class WhitelistSandbox implements Sandbox {

  private static final String WILDCARD_PREFIX = "*.";
  private static final String REGEX_WHITESPACE = "\\s+";

  private final List<Path> allowedRoots;
  private final Set<String> allowedCommands;
  private final List<String> allowedDomainPatterns;

  /**
   * 构造应用层白名单沙箱.
   *
   * @param fileProps 文件路径白名单配置
   * @param shellProps Shell 命令白名单配置
   * @param httpProps HTTP 域名白名单配置
   */
  public WhitelistSandbox(
      FileSandboxProperties fileProps,
      ShellSandboxProperties shellProps,
      HttpSandboxProperties httpProps) {
    this.allowedRoots =
        fileProps != null && fileProps.allowedPaths() != null
            ? fileProps.allowedPaths().stream()
                .map(p -> Path.of(p).toAbsolutePath().normalize())
                .toList()
            : List.of();

    this.allowedCommands =
        shellProps != null && shellProps.allowedCommands() != null
            ? Set.copyOf(shellProps.allowedCommands())
            : Set.of();

    this.allowedDomainPatterns =
        httpProps != null && httpProps.allowedDomains() != null
            ? List.copyOf(httpProps.allowedDomains())
            : List.of();
  }

  @Override
  public void enforce(SandboxAction action) {
    Objects.requireNonNull(action, "action must not be null");
    switch (action.type()) {
      case FILE_READ:
      case FILE_WRITE:
        checkFilePath(action.target());
        break;
      case SHELL_COMMAND:
        checkShellCommand(action.target());
        break;
      case HTTP_REQUEST:
        checkHttpUrl(action.target());
        break;
      default:
        throw new SandboxViolationException("不支持的沙箱动作类型: " + action.type());
    }
  }

  @Override
  public boolean check(String target) {
    if (target == null || target.isBlank()) {
      return false;
    }
    try {
      Path targetPath = Path.of(target).toAbsolutePath().normalize();
      return allowedRoots.stream().anyMatch(targetPath::startsWith);
    } catch (InvalidPathException e) {
      return false;
    }
  }

  private void checkFilePath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new SandboxViolationException("文件路径不能为空");
    }
    Path target = Path.of(rawPath).toAbsolutePath().normalize();
    boolean allowed = allowedRoots.stream().anyMatch(target::startsWith);
    if (!allowed) {
      throw new SandboxViolationException("路径不在白名单内: " + rawPath);
    }
  }

  private void checkShellCommand(String command) {
    if (command == null || command.isBlank()) {
      throw new SandboxViolationException("Shell 命令不能为空");
    }
    String trimmed = command.trim();
    String[] tokens = trimmed.split(REGEX_WHITESPACE);
    if (tokens.length == 0 || tokens[0].isBlank()) {
      throw new SandboxViolationException("无法提取有效的 Shell 命令");
    }
    String firstToken = tokens[0];
    if (!allowedCommands.contains(firstToken)) {
      throw new SandboxViolationException("命令不在白名单内: " + firstToken);
    }
  }

  private void checkHttpUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new SandboxViolationException("HTTP URL 不能为空");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new SandboxViolationException("无效的 HTTP URL 格式: " + url, e);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new SandboxViolationException("无法从 URL 解析有效域名: " + url);
    }
    boolean allowed =
        allowedDomainPatterns.stream().anyMatch(pattern -> matchesDomain(host, pattern));
    if (!allowed) {
      throw new SandboxViolationException("域名不在白名单内: " + host);
    }
  }

  private boolean matchesDomain(String host, String pattern) {
    if (pattern == null || pattern.isBlank()) {
      return false;
    }
    String trimmedPattern = pattern.trim().toLowerCase(Locale.ROOT);
    String normalizedHost = host.toLowerCase(Locale.ROOT);

    if (trimmedPattern.startsWith(WILDCARD_PREFIX)) {
      String suffix = trimmedPattern.substring(1);
      if (normalizedHost.endsWith(suffix)) {
        return true;
      }
      String rootDomain = trimmedPattern.substring(2);
      return normalizedHost.equals(rootDomain);
    }
    return normalizedHost.equals(trimmedPattern);
  }
}
