package com.oryxos.web.controller;

import com.oryxos.core.OryxTool;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.ToolSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool 元数据只读 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "Tools", description = "Tool 元数据")
public class ToolApiController {

  private final ToolRegistry toolRegistry;

  /** 创建 Tool 查询 API. */
  public ToolApiController(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  /** 列出 Tool 元数据，不执行工具. */
  @GetMapping
  @Operation(summary = "列出 Tool 元数据")
  public ApiResponse<List<ToolSummary>> list() {
    List<ToolSummary> tools =
        toolRegistry.getAllTools().stream()
            .sorted(Comparator.comparing(OryxTool::getName))
            .map(this::toSummary)
            .toList();
    return ApiResponse.success(tools);
  }

  private ToolSummary toSummary(OryxTool tool) {
    return new ToolSummary(tool.getName(), tool.getDescription(), tool.getInputSchema());
  }
}
