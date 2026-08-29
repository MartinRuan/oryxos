package com.oryxos.web.common;

/**
 * OryxOS 基础业务异常.
 *
 * @author OryxOS Team
 */
public class OryxException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  /**
   * 基于错误码构造异常.
   *
   * @param errorCode 错误码
   */
  public OryxException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /**
   * 基于错误码与自定义描述构造异常.
   *
   * @param errorCode 错误码
   * @param customMessage 自定义错误描述
   */
  public OryxException(ErrorCode errorCode, String customMessage) {
    super(customMessage);
    this.errorCode = errorCode;
  }

  /**
   * 基于错误码、自定义描述与根因异常构造异常.
   *
   * @param errorCode 错误码
   * @param customMessage 自定义错误描述
   * @param cause 异常根因
   */
  public OryxException(ErrorCode errorCode, String customMessage, Throwable cause) {
    super(customMessage, cause);
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
}
