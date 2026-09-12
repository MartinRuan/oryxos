package com.oryxos.web.controller;

import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.AgentReply;
import com.oryxos.web.dto.MessageRequest;
import com.oryxos.web.service.AgentInvocationRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一次性 Agent 调用 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "Agents", description = "一次性 Agent 调用")
public class AgentApiController {

  private static final String INVOKE_CHANNEL = "invoke";

  private final ProfileRegistry profileRegistry;
  private final SessionManager sessionManager;
  private final AgentInvocationRunner invocationRunner;

  /**
   * 创建 Agent 调用 API.
   *
   * @param profileRegistry Profile 注册中心
   * @param sessionManager 会话管理器
   * @param invocationRunner 调用执行器
   */
  public AgentApiController(
      ProfileRegistry profileRegistry,
      SessionManager sessionManager,
      AgentInvocationRunner invocationRunner) {
    this.profileRegistry = profileRegistry;
    this.sessionManager = sessionManager;
    this.invocationRunner = invocationRunner;
  }

  /**
   * 按 Agent 名称执行一次任务.
   *
   * @param name Agent/Profile 名称
   * @param request 消息请求
   * @return 最终回复
   */
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
