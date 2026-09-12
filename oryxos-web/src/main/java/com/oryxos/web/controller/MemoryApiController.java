package com.oryxos.web.controller;

import com.oryxos.memory.MemoryService;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.MemoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 长期记忆只读 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1/memory")
@Tag(name = "Memory", description = "长期记忆只读视图")
public class MemoryApiController {

  private final MemoryService memoryService;

  /** 创建 Memory 查询 API. */
  public MemoryApiController(MemoryService memoryService) {
    this.memoryService = memoryService;
  }

  /** 读取长期记忆. */
  @GetMapping
  @Operation(summary = "读取长期记忆")
  public ApiResponse<MemoryView> load() {
    return ApiResponse.success(new MemoryView(memoryService.load()));
  }
}
