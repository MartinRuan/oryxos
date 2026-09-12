package com.oryxos.web.controller;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.CreateSessionRequest;
import com.oryxos.web.dto.MessageRequest;
import com.oryxos.web.dto.MessageResponse;
import com.oryxos.web.dto.SessionDetail;
import com.oryxos.web.dto.SessionDtoMapper;
import com.oryxos.web.dto.SessionSummary;
import com.oryxos.web.service.AgentInvocationRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有状态 Agent 会话 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "有状态 Agent 会话")
public class SessionApiController {

  private static final String WEB_CHANNEL = "web";
  private static final String ARCHIVED_STATUS = "ARCHIVED";

  private final SessionManager sessionManager;
  private final ProfileRegistry profileRegistry;
  private final AgentInvocationRunner invocationRunner;
  private final SessionDtoMapper mapper;

  /** 创建会话 API. */
  public SessionApiController(
      SessionManager sessionManager,
      ProfileRegistry profileRegistry,
      AgentInvocationRunner invocationRunner,
      SessionDtoMapper mapper) {
    this.sessionManager = sessionManager;
    this.profileRegistry = profileRegistry;
    this.invocationRunner = invocationRunner;
    this.mapper = mapper;
  }

  /** 创建或解析 Web 会话. */
  @PostMapping
  @Operation(summary = "创建或解析 Web 会话")
  public ApiResponse<SessionDetail> create(@Valid @RequestBody CreateSessionRequest request) {
    profileRegistry.getRequiredProfile(request.profileName());
    Session session =
        sessionManager.getOrCreate(WEB_CHANNEL, request.userId(), request.profileName());
    return ApiResponse.success(mapper.toDetail(session));
  }

  /** 列出会话摘要. */
  @GetMapping
  @Operation(summary = "列出会话摘要")
  public ApiResponse<List<SessionSummary>> list() {
    return ApiResponse.success(sessionManager.list().stream().map(mapper::toSummary).toList());
  }

  /** 向活动会话发送消息. */
  @PostMapping("/{id}/messages")
  @Operation(summary = "向活动会话发送消息")
  public ApiResponse<MessageResponse> sendMessage(
      @PathVariable String id, @Valid @RequestBody MessageRequest request) {
    Session session = requireSession(id);
    if (ARCHIVED_STATUS.equals(session.getStatus())) {
      throw new OryxException(StandardErrorCode.NOT_FOUND, "Session not found: " + id);
    }
    String reply = invocationRunner.run(session, request.content());
    return ApiResponse.success(new MessageResponse(session.getId(), reply));
  }

  /** 查询会话详情. */
  @GetMapping("/{id}")
  @Operation(summary = "查询会话详情")
  public ApiResponse<SessionDetail> detail(@PathVariable String id) {
    return ApiResponse.success(mapper.toDetail(requireSession(id)));
  }

  /** 幂等归档会话. */
  @DeleteMapping("/{id}")
  @Operation(summary = "归档会话")
  public ApiResponse<SessionDetail> archive(@PathVariable String id) {
    Session session = requireSession(id);
    if (!ARCHIVED_STATUS.equals(session.getStatus())) {
      sessionManager.archive(id);
      session.setStatus(ARCHIVED_STATUS);
      session.setArchivedAt(LocalDateTime.now());
    }
    return ApiResponse.success(mapper.toDetail(session));
  }

  private Session requireSession(String id) {
    return sessionManager
        .get(id)
        .orElseThrow(
            () -> new OryxException(StandardErrorCode.NOT_FOUND, "Session not found: " + id));
  }
}
