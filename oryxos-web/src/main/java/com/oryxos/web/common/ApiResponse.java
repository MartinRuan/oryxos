package com.oryxos.web.common;

import com.oryxos.core.exception.ErrorCode;
import com.oryxos.core.exception.StandardErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 统一 REST API 响应信封.
 *
 * @param <T> 数据响应体类型
 * @author OryxOS Team
 */
@Schema(description = "统一 API 响应信封")
public class ApiResponse<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "状态响应码，0 为成功，非 0 为异常", example = "0")
  private int code;

  @Schema(description = "响应状态或错误描述信息", example = "Success")
  private String message;

  @Schema(description = "业务响应数据负载")
  private T data;

  @Schema(description = "响应生成时间戳 (毫秒)", example = "1724918400000")
  private long timestamp;

  /** 默认无参构造函数. */
  public ApiResponse() {
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 全参构造函数.
   *
   * @param code 响应码
   * @param message 响应信息
   * @param data 响应数据
   */
  public ApiResponse(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 构造空成功响应.
   *
   * @param <T> 泛型类型
   * @return 成功响应体
   */
  public static <T> ApiResponse<T> success() {
    return new ApiResponse<>(
        StandardErrorCode.SUCCESS.getCode(), StandardErrorCode.SUCCESS.getMessage(), null);
  }

  /**
   * 构造带数据的成功响应.
   *
   * @param data 数据载荷
   * @param <T> 泛型类型
   * @return 成功响应体
   */
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(
        StandardErrorCode.SUCCESS.getCode(), StandardErrorCode.SUCCESS.getMessage(), data);
  }

  /**
   * 构造自定义消息与数据的成功响应.
   *
   * @param message 自定义消息
   * @param data 数据载荷
   * @param <T> 泛型类型
   * @return 成功响应体
   */
  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(StandardErrorCode.SUCCESS.getCode(), message, data);
  }

  /**
   * 构造标准错误响应.
   *
   * @param errorCode 错误码契约
   * @param <T> 泛型类型
   * @return 错误响应体
   */
  public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
    return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
  }

  /**
   * 构造自定义消息的标准错误响应.
   *
   * @param errorCode 错误码契约
   * @param message 自定义错误信息
   * @param <T> 泛型类型
   * @return 错误响应体
   */
  public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
    return new ApiResponse<>(errorCode.getCode(), message, null);
  }

  /**
   * 构造自定义错误码与消息的错误响应.
   *
   * @param code 错误码
   * @param message 错误信息
   * @param <T> 泛型类型
   * @return 错误响应体
   */
  public static <T> ApiResponse<T> fail(int code, String message) {
    return new ApiResponse<>(code, message, null);
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
}
