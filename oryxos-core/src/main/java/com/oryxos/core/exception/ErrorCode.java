package com.oryxos.core.exception;

/**
 * 统一错误码契约接口.
 *
 * @author oryxos
 */
public interface ErrorCode {

  /**
   * 获取数字错误码.
   *
   * @return 错误码
   */
  int getCode();

  /**
   * 获取错误描述信息.
   *
   * @return 错误信息
   */
  String getMessage();
}
