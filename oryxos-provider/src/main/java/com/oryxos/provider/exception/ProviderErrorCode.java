package com.oryxos.provider.exception;

import com.oryxos.core.exception.ErrorCode;

/**
 * Provider 模块专属错误码定义.
 *
 * @author oryxos
 */
public enum ProviderErrorCode implements ErrorCode {

  /** 请求的 Provider 名称未在系统中注册. */
  PROVIDER_NOT_FOUND(40410, "Provider not registered in system"),

  /** Provider API Key 缺失、未配置环境变量或鉴权失败. */
  PROVIDER_AUTH_FAILED(40110, "Provider authentication failed or API key missing"),

  /** 模型调用超出 120 秒超时阈值. */
  PROVIDER_TIMEOUT(50410, "Provider call timed out"),

  /** 触发远程服务配额限制或 429 请求过多. */
  PROVIDER_RATE_LIMIT(42910, "Provider rate limit exceeded or quota exhausted"),

  /** 模型返回畸变 JSON 或无法解析的参数格式. */
  PROVIDER_RESPONSE_MALFORMED(50210, "Provider returned malformed response or arguments"),

  /** 远程服务 5xx 故障且重试耗尽. */
  PROVIDER_SERVICE_UNAVAILABLE(50310, "Provider service temporarily unavailable");

  private final int code;
  private final String message;

  ProviderErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public int getCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
