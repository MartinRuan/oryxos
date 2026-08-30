package com.oryxos.web.controller;

import com.oryxos.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统级基础端点控制器.
 *
 * @author OryxOS Team
 */
@Tag(name = "System", description = "系统监控与状态端点")
@RestController
@RequestMapping("/api/v1")
public class SystemController {

  /**
   * 检查系统健康状态.
   *
   * @return 健康检查响应
   */
  @Operation(summary = "系统健康状态检查")
  @GetMapping("/health")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.success(
        Map.of(
            "status", "UP",
            "version", "0.1.0-SNAPSHOT",
            "timestamp", System.currentTimeMillis()));
  }

  /**
   * 获取系统运行信息与配置概览.
   *
   * @return 系统信息响应
   */
  @Operation(summary = "系统运行信息与配置概览")
  @GetMapping("/info")
  public ApiResponse<Map<String, Object>> info() {
    return ApiResponse.success(
        Map.of(
            "name",
            "OryxOS",
            "description",
            "Distributed AI Agent OS for Enterprise",
            "javaVersion",
            System.getProperty("java.version"),
            "osName",
            System.getProperty("os.name")));
  }
}
