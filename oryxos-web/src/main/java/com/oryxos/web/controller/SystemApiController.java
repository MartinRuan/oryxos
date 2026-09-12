package com.oryxos.web.controller;

import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.ProviderView;
import com.oryxos.web.dto.SystemInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康与安全运行信息 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "系统健康与运行状态")
public class SystemApiController {

  private static final String APPLICATION_NAME = "OryxOS";
  private static final String APPLICATION_VERSION = "0.1.0-SNAPSHOT";

  private final ProviderRegistry providerRegistry;

  /** 创建系统状态 API. */
  public SystemApiController(ProviderRegistry providerRegistry) {
    this.providerRegistry = providerRegistry;
  }

  /** 查询服务健康状态. */
  @GetMapping("/health")
  @Operation(summary = "系统健康状态")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.success(
        Map.of("status", "UP", "version", APPLICATION_VERSION, "responseTimeMs", 0L));
  }

  /** 查询系统与 Provider 安全运行信息. */
  @GetMapping("/info")
  @Operation(summary = "系统运行信息")
  public ApiResponse<SystemInfo> info() {
    List<ProviderView> providers =
        providerRegistry.listDescriptors().stream()
            .sorted(Comparator.comparing(ProviderDescriptor::getName))
            .map(this::toView)
            .toList();
    return ApiResponse.success(
        new SystemInfo(
            APPLICATION_NAME, APPLICATION_VERSION, System.getProperty("java.version"), providers));
  }

  private ProviderView toView(ProviderDescriptor descriptor) {
    return new ProviderView(
        descriptor.getName(),
        descriptor.getType(),
        descriptor.getDefaultModel(),
        List.copyOf(descriptor.getSupportedModels()),
        providerRegistry.isAvailable(descriptor.getName()));
  }
}
