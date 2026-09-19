package com.oryxos.web.controller;

import com.oryxos.core.profile.AgentStore;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.FileNode;
import com.oryxos.web.dto.FileNode.NodeType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 工作区只读浏览 API.
 *
 * @author oryxos
 */
@RestController
@RequestMapping("/api/v1/workspace")
@Tag(name = "Workspace", description = "Agent 工作区只读浏览")
public class WorkspaceApiController {

  private final AgentStore agentStore;

  public WorkspaceApiController(AgentStore agentStore) {
    this.agentStore = agentStore;
  }

  @GetMapping("/tree")
  @Operation(summary = "读取 agents/archive 目录树")
  public ApiResponse<List<FileNode>> tree() {
    return ApiResponse.success(List.of(directory("agents"), directory("archive")));
  }

  @GetMapping("/file")
  @Operation(summary = "读取工作区 UTF-8 文本文件")
  public ApiResponse<String> file(@RequestParam String path) {
    return ApiResponse.success(agentStore.readWorkspaceFile(path));
  }

  private FileNode directory(String path) {
    List<FileNode> children =
        agentStore.listDirectory(path).stream()
            .map(
                entry ->
                    entry.directory()
                        ? directory(entry.path())
                        : new FileNode(
                            entry.name(), entry.path(), NodeType.FILE, entry.readable(), List.of()))
            .toList();
    String name = path.substring(path.lastIndexOf('/') + 1);
    return new FileNode(name, path, NodeType.DIRECTORY, true, children);
  }
}
