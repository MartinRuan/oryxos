package com.oryxos.core.exception;

/**
 * 平台通用标准错误码枚举.
 *
 * @author oryxos
 */
public enum StandardErrorCode implements ErrorCode {

  /** 成功响应. */
  SUCCESS(0, "Success"),

  /** 客户端参数无效 (400 Bad Request). */
  INVALID_PARAMETER(40000, "Invalid parameter"),

  /** 未经认证/身份凭据无效 (401 Unauthorized). */
  UNAUTHORIZED(40100, "Unauthorized access"),

  /** 访问被拒绝/权限不足 (403 Forbidden). */
  FORBIDDEN(40300, "Access forbidden"),

  /** 请求资源未找到 (404 Not Found). */
  NOT_FOUND(40400, "Resource not found"),

  /** Profile 未找到. */
  PROFILE_NOT_FOUND(40401, "Profile not found"),

  /** Provider 未找到. */
  PROVIDER_NOT_FOUND(40402, "Provider not found"),

  /** 请求方法不支持 (405 Method Not Allowed). */
  METHOD_NOT_ALLOWED(40500, "Method not allowed"),

  /** 请求过于频繁/限流 (429 Too Many Requests). */
  TOO_MANY_REQUESTS(42900, "Too many requests"),

  /** 系统内部未知异常 (500 Internal Server Error). */
  INTERNAL_ERROR(50000, "Internal server error"),

  /** 服务不可用 / Provider 调用失败 (503 Service Unavailable). */
  SERVICE_UNAVAILABLE(50300, "Service unavailable");

  private final int code;
  private final String message;

  StandardErrorCode(int code, String message) {
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
