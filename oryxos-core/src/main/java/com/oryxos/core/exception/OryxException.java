package com.oryxos.core.exception;

/**
 * OryxOS 统一业务与运行时异常基类.
 *
 * @author oryxos
 */
public class OryxException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  /**
   * 基于错误码构造异常.
   *
   * @param errorCode 错误码契约
   */
  public OryxException(ErrorCode errorCode) {
    super(errorCode != null ? errorCode.getMessage() : "Unknown error");
    this.errorCode = errorCode;
  }

  /**
   * 基于错误码与自定义描述构造异常.
   *
   * @param errorCode 错误码契约
   * @param customMessage 自定义错误描述
   */
  public OryxException(ErrorCode errorCode, String customMessage) {
    super(
        customMessage != null ? customMessage : (errorCode != null ? errorCode.getMessage() : ""));
    this.errorCode = errorCode;
  }

  /**
   * 基于错误码、自定义描述与根因异常构造异常.
   *
   * @param errorCode 错误码契约
   * @param customMessage 自定义错误描述
   * @param cause 异常根因
   */
  public OryxException(ErrorCode errorCode, String customMessage, Throwable cause) {
    super(
        customMessage != null ? customMessage : (errorCode != null ? errorCode.getMessage() : ""),
        cause);
    this.errorCode = errorCode;
  }

  /**
   * 获取错误码契约.
   *
   * @return 错误码实例
   */
  public ErrorCode getErrorCode() {
    return errorCode;
  }

  /**
   * 获取数字错误码.
   *
   * @return 数字错误码
   */
  public int getCode() {
    return errorCode != null ? errorCode.getCode() : 50000;
  }
}
