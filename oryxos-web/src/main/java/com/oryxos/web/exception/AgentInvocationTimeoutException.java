package com.oryxos.web.exception;

/**
 * Agent 调用超过 Web 请求等待上限时抛出的异常.
 *
 * @author OryxOS Team
 */
public class AgentInvocationTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 创建默认超时异常. */
  public AgentInvocationTimeoutException() {
    super("Agent invocation timed out");
  }
}
