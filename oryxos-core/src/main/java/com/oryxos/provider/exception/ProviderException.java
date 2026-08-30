package com.oryxos.provider.exception;

import com.oryxos.core.exception.ErrorCode;
import com.oryxos.core.exception.OryxException;

/**
 * Provider 模块统一业务与运行时异常.
 *
 * @author oryxos
 */
public class ProviderException extends OryxException {

  private static final long serialVersionUID = 1L;

  private final String provider;
  private final String model;

  /**
   * 基于错误码、提供商名称、模型名称及消息构造异常.
   *
   * @param errorCode 错误码契约
   * @param provider 提供商名称
   * @param model 模型型号
   * @param message 异常描述
   */
  public ProviderException(ErrorCode errorCode, String provider, String model, String message) {
    super(errorCode, message);
    this.provider = provider;
    this.model = model;
  }

  /**
   * 基于错误码、提供商名称、模型名称、消息及根因异常构造异常.
   *
   * @param errorCode 错误码契约
   * @param provider 提供商名称
   * @param model 模型型号
   * @param message 异常描述
   * @param cause 异常根因
   */
  public ProviderException(
      ErrorCode errorCode, String provider, String model, String message, Throwable cause) {
    super(errorCode, message, cause);
    this.provider = provider;
    this.model = model;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }
}
