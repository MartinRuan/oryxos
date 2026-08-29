package com.oryxos.web.common;

/**
 * 标准错误码枚举定义.
 *
 * @author OryxOS Team
 */
public enum StandardErrorCode implements ErrorCode {
  /** 成功. */
  SUCCESS(0, "Success"),

  /** 错误的请求参数. */
  BAD_REQUEST(400, "Bad Request"),

  /** 未授权认证. */
  UNAUTHORIZED(401, "Unauthorized"),

  /** 禁止访问. */
  FORBIDDEN(403, "Forbidden"),

  /** 资源不存在. */
  NOT_FOUND(404, "Resource Not Found"),

  /** 不支持的 HTTP 请求方法. */
  METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

  /** 服务器内部错误. */
  INTERNAL_SERVER_ERROR(500, "Internal Server Error"),

  /** 服务不可用. */
  SERVICE_UNAVAILABLE(503, "Service Unavailable");

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
