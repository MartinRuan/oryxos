package com.oryxos.core.tool.impl;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;
import com.oryxos.core.tool.ToolAuditRecorder;
import com.oryxos.core.tool.ToolExecutor;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一工具安全执行器实现.
 *
 * <p>具备首行沙箱预留检查、执行分发、全量调用审计（成功与失败均实时落库），且执行异常绝不静默吞掉.
 *
 * @author oryxos
 */
public class ToolExecutorImpl implements ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(ToolExecutorImpl.class);

  private final Map<String, OryxTool> toolMap = new ConcurrentHashMap<>();
  private final ToolAuditRecorder auditRecorder;

  /**
   * 构造工具执行器.
   *
   * @param auditRecorder 审计记录器（可选，可为 null）
   */
  public ToolExecutorImpl(ToolAuditRecorder auditRecorder) {
    this.auditRecorder = auditRecorder;
  }

  /**
   * 构造工具执行器（带初始工具列表）.
   *
   * @param tools 初始工具列表
   * @param auditRecorder 审计记录器
   */
  public ToolExecutorImpl(Collection<OryxTool> tools, ToolAuditRecorder auditRecorder) {
    this.auditRecorder = auditRecorder;
    if (tools != null) {
      for (OryxTool tool : tools) {
        registerTool(tool);
      }
    }
  }

  /**
   * 注册工具.
   *
   * @param tool 工具实现实例
   */
  public void registerTool(OryxTool tool) {
    if (tool != null && tool.getName() != null) {
      toolMap.put(tool.getName().trim(), tool);
    }
  }

  /**
   * 根据名称获取工具.
   *
   * @param toolName 工具名称
   * @return OryxTool Optional
   */
  public Optional<OryxTool> getTool(String toolName) {
    if (toolName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(toolMap.get(toolName.trim()));
  }

  @Override
  public ToolResult execute(String sessionId, ToolCallIntent call) {
    Objects.requireNonNull(call, "ToolCallIntent must not be null");
    String sid = sessionId != null ? sessionId : "unassigned-session";
    String toolName = call.getName() != null ? call.getName().trim() : "unknown-tool";
    String inputJson = call.getArgumentsJson() != null ? call.getArgumentsJson() : "{}";

    long startTime = System.currentTimeMillis();

    // 1. 沙箱/白名单前置安全检查（预留位，第 24 节完整接入 Sandbox.enforce）
    enforceSandbox(sid, call);

    OryxTool tool = toolMap.get(toolName);
    if (tool == null) {
      long duration = System.currentTimeMillis() - startTime;
      String errorMsg = "Tool not found: " + toolName;
      recordAudit(sid, toolName, inputJson, null, false, errorMsg, duration);
      return ToolResult.failure(errorMsg, false);
    }

    try {
      ToolResult result = tool.execute(inputJson);
      long duration = System.currentTimeMillis() - startTime;
      if (result != null && result.isSuccess()) {
        recordAudit(sid, toolName, inputJson, result.getContent(), true, null, duration);
        return result;
      } else {
        String errorMsg = result != null ? result.getErrorMessage() : "Tool returned empty failure";
        recordAudit(sid, toolName, inputJson, null, false, errorMsg, duration);
        return result != null ? result : ToolResult.failure(errorMsg, false);
      }
    } catch (RuntimeException e) {
      long duration = System.currentTimeMillis() - startTime;
      recordAudit(sid, toolName, inputJson, null, false, e.getMessage(), duration);
      log.error("Tool execution failed for tool {} (session {})", toolName, sid, e);
      throw e;
    }
  }

  private void enforceSandbox(String sessionId, ToolCallIntent call) {
    // 预留沙箱检查桩点，第 24 节接入 WhitelistSandbox
  }

  private void recordAudit(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs) {
    if (auditRecorder != null) {
      try {
        auditRecorder.record(
            sessionId, toolName, inputJson, resultJson, success, errorMessage, durationMs);
      } catch (Exception e) {
        log.warn("Failed to record tool invocation audit into storage", e);
      }
    }
  }
}
