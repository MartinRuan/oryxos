package com.oryxos.web.controller;

import com.oryxos.memory.MemoryService;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.MemoryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * 读取长期记忆；指定 Agent 时只返回该 Agent 与历史共享记忆.
   *
   * @param agent Agent 名称，可为空以保持全量查询兼容
   * @return 长期记忆只读视图
   */
  @GetMapping
  @Operation(summary = "读取长期记忆")
  public ApiResponse<MemoryView> load(@RequestParam(required = false) String agent) {
    String content =
        agent == null || agent.isBlank() ? memoryService.load() : memoryService.load(agent.trim());
    return ApiResponse.success(new MemoryView(content));
  }
}
