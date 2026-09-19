package com.oryxos.web.controller;

import com.oryxos.core.model.AgentView;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.AgentLifecycleService;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.AgentReply;
import com.oryxos.web.dto.CreateAgentRequest;
import com.oryxos.web.dto.GenerateAgentRequest;
import com.oryxos.web.dto.MessageRequest;
import com.oryxos.web.dto.UpdateAgentRequest;
import com.oryxos.web.service.AgentInvocationRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态 Agent 管理与一次性调用 REST API.
 *
 * @author oryxos
 */
@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "Agents", description = "动态 Agent 管理与调用")
public class AgentApiController {

  private static final String INVOKE_CHANNEL = "invoke";
  private final ProfileRegistry profileRegistry;
  private final SessionManager sessionManager;
  private final AgentInvocationRunner invocationRunner;
  private final AgentLifecycleService lifecycleService;

  /** 创建动态 Agent 管理控制器. */
  @Autowired
  public AgentApiController(
      ProfileRegistry profileRegistry,
      SessionManager sessionManager,
      AgentInvocationRunner invocationRunner,
      AgentLifecycleService lifecycleService) {
    this.profileRegistry = profileRegistry;
    this.sessionManager = sessionManager;
    this.invocationRunner = invocationRunner;
    this.lifecycleService = lifecycleService;
  }

  /** 保留用于仅验证 invoke 契约的轻量构造器. */
  public AgentApiController(
      ProfileRegistry profileRegistry,
      SessionManager sessionManager,
      AgentInvocationRunner invocationRunner) {
    this(profileRegistry, sessionManager, invocationRunner, null);
  }

  @PostMapping("/generate")
  @Operation(summary = "一句话生成 Agent 草稿")
  public ApiResponse<String> generate(@Valid @RequestBody GenerateAgentRequest request) {
    return ApiResponse.success(lifecycleService.generate(request.sentence()));
  }

  @PostMapping
  @Operation(summary = "创建 Agent")
  public ApiResponse<AgentView> create(@Valid @RequestBody CreateAgentRequest request) {
    return ApiResponse.success(lifecycleService.create(request.name(), request.toAgentMarkdown()));
  }

  @GetMapping
  @Operation(summary = "列出动态 Agent")
  public ApiResponse<List<AgentView>> list() {
    return ApiResponse.success(lifecycleService.list());
  }

  @GetMapping("/{name}")
  @Operation(summary = "读取 Agent 定义")
  public ApiResponse<AgentView> detail(@PathVariable String name) {
    return ApiResponse.success(lifecycleService.get(name));
  }

  @PutMapping("/{name}")
  @Operation(summary = "更新 Agent")
  public ApiResponse<AgentView> update(
      @PathVariable String name, @Valid @RequestBody UpdateAgentRequest request) {
    return ApiResponse.success(lifecycleService.update(name, request.toAgentMarkdown(name)));
  }

  @DeleteMapping("/{name}")
  @Operation(summary = "删除并归档 Agent")
  public ApiResponse<Void> delete(@PathVariable String name) {
    lifecycleService.delete(name);
    return ApiResponse.success();
  }

  /** 按 Agent 名称执行一次独立调用. */
  @PostMapping("/{name}/invoke")
  @Operation(summary = "一次性调用 Agent")
  public ApiResponse<AgentReply> invoke(
      @PathVariable String name, @Valid @RequestBody MessageRequest request) {
    profileRegistry.getRequiredProfile(name);
    String invocationUser = UUID.randomUUID().toString();
    Session session = sessionManager.getOrCreate(INVOKE_CHANNEL, invocationUser, name);
    String reply = invocationRunner.run(session, request.content());
    return ApiResponse.success(new AgentReply(reply));
  }
}
