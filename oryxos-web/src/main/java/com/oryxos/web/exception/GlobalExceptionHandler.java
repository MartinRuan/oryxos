package com.oryxos.web.exception;

import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.common.OryxException;
import com.oryxos.web.common.StandardErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局统一 REST API 异常拦截处理器.
 *
 * @author OryxOS Team
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * 处理业务异常 OryxException.
   *
   * @param ex 业务异常实例
   * @return 错误响应结构
   */
  @ExceptionHandler(OryxException.class)
  public ApiResponse<Void> handleOryxException(OryxException ex) {
    if (log.isWarnEnabled()) {
      log.warn(
          "Business exception occurred: code={}, message={}",
          ex.getErrorCode().getCode(),
          sanitize(ex.getMessage()));
    }
    return ApiResponse.fail(ex.getErrorCode(), ex.getMessage());
  }

  /**
   * 处理数据绑定与参数校验异常.
   *
   * @param ex 校验异常实例
   * @return 错误响应结构
   */
  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidationException(Exception ex) {
    StringBuilder message = new StringBuilder();
    if (ex instanceof MethodArgumentNotValidException manv) {
      for (FieldError fieldError : manv.getBindingResult().getFieldErrors()) {
        message
            .append(fieldError.getField())
            .append(": ")
            .append(fieldError.getDefaultMessage())
            .append("; ");
      }
    } else if (ex instanceof BindException be) {
      for (FieldError fieldError : be.getBindingResult().getFieldErrors()) {
        message
            .append(fieldError.getField())
            .append(": ")
            .append(fieldError.getDefaultMessage())
            .append("; ");
      }
    }
    String errorMsg =
        message.isEmpty() ? StandardErrorCode.BAD_REQUEST.getMessage() : message.toString().trim();
    if (log.isWarnEnabled()) {
      log.warn("Validation error: {}", sanitize(errorMsg));
    }
    return ApiResponse.fail(StandardErrorCode.BAD_REQUEST, errorMsg);
  }

  /**
   * 处理非法参数异常.
   *
   * @param ex 非法参数异常
   * @return 错误响应结构
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
    if (log.isWarnEnabled()) {
      log.warn("Illegal argument: {}", sanitize(ex.getMessage()));
    }
    return ApiResponse.fail(StandardErrorCode.BAD_REQUEST, ex.getMessage());
  }

  /**
   * 处理不支持的 HTTP 请求方法异常.
   *
   * @param ex 方法不支持异常
   * @return 错误响应结构
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    if (log.isWarnEnabled()) {
      log.warn("HTTP method not supported: {}", sanitize(ex.getMessage()));
    }
    return ApiResponse.fail(StandardErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
  }

  /**
   * 处理找不到静态资源或路由异常.
   *
   * @param ex 资源不存在异常
   * @return 错误响应结构
   */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException ex) {
    if (log.isWarnEnabled()) {
      log.warn("Resource not found: {}", sanitize(ex.getMessage()));
    }
    return ApiResponse.fail(StandardErrorCode.NOT_FOUND, ex.getMessage());
  }

  /**
   * 兜底处理未捕获的服务器通用异常.
   *
   * @param ex 异常实例
   * @return 错误响应结构
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleGenericException(Exception ex) {
    log.error("Unhandled server exception", ex);
    return ApiResponse.fail(StandardErrorCode.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  /**
   * 清洗日志文本，防御 CRLF 注入.
   *
   * @param input 原始文本
   * @return 清洗后文本
   */
  private String sanitize(String input) {
    if (input == null) {
      return "";
    }
    return input.replace('\n', '_').replace('\r', '_');
  }
}
