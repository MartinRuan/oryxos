package com.oryxos.tool.sandbox;

/**
 * 沙箱安全违规异常.
 *
 * <p>当 Agent 试图访问未授权文件、执行非白名单命令或请求非白名单域名时抛出， 异常信息将由 ToolExecutor 统一捕获并落库审计，最终回填给模型.
 *
 * @author OryxOS Team
 */
public class SandboxViolationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造沙箱违规异常.
   *
   * @param message 违规详情说明
   */
  public SandboxViolationException(String message) {
    super(message);
  }

  /**
   * 构造包含底层原因的沙箱违规异常.
   *
   * @param message 违规详情说明
   * @param cause 底层异常
   */
  public SandboxViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
