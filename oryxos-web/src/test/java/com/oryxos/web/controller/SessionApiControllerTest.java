package com.oryxos.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import com.oryxos.web.dto.SessionDtoMapper;
import com.oryxos.web.exception.GlobalExceptionHandler;
import com.oryxos.web.service.AgentInvocationRunner;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SessionApiControllerTest {

  private SessionManager sessionManager;
  private ProfileRegistry profileRegistry;
  private AgentInvocationRunner invocationRunner;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    sessionManager = mock(SessionManager.class);
    profileRegistry = mock(ProfileRegistry.class);
    invocationRunner = mock(AgentInvocationRunner.class);
    SessionApiController controller =
        new SessionApiController(
            sessionManager, profileRegistry, invocationRunner, new SessionDtoMapper());
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("创建并列出会话摘要且不暴露消息正文")
  void 创建并列出会话摘要且不暴露消息正文() throws Exception {
    Profile profile = new Profile();
    profile.setName("ops-agent");
    Session session = new Session("web:user-1:ops-agent", "ops-agent", "web", "user-1");
    session.append(ChatMessage.user("secret-content"));
    when(profileRegistry.getRequiredProfile("ops-agent")).thenReturn(profile);
    when(sessionManager.getOrCreate("web", "user-1", "ops-agent")).thenReturn(session);
    when(sessionManager.list()).thenReturn(List.of(session));

    mockMvc
        .perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"ops-agent\",\"userId\":\"user-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(session.getId()))
        .andExpect(jsonPath("$.data.channel").value("web"));

    mockMvc
        .perform(get("/api/v1/sessions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(1)))
        .andExpect(jsonPath("$.data[0].messageCount").value(1))
        .andExpect(content().string(not(org.hamcrest.Matchers.containsString("secret-content"))));
  }

  @Test
  @DisplayName("发送消息只委托统一Agent执行器一次")
  void 发送消息只委托统一Agent执行器一次() throws Exception {
    Session session = new Session("web:user-1:ops-agent", "ops-agent", "web", "user-1");
    when(sessionManager.get(session.getId())).thenReturn(Optional.of(session));
    when(invocationRunner.run(session, "hello")).thenReturn("reply");

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", session.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
        .andExpect(jsonPath("$.data.reply").value("reply"));

    verify(invocationRunner).run(session, "hello");
  }

  @Test
  @DisplayName("消息校验和会话状态阻止无效调用")
  void 消息校验和会话状态阻止无效调用() throws Exception {
    String tooLong = "x".repeat(32769);
    mockMvc
        .perform(
            post("/api/v1/sessions/missing/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest());
    verify(invocationRunner, never()).run(any(), any());

    Session archived = new Session("archived", "ops-agent", "web", "u");
    archived.setStatus("ARCHIVED");
    when(sessionManager.get("archived")).thenReturn(Optional.of(archived));
    mockMvc
        .perform(
            post("/api/v1/sessions/archived/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("详情只返回最新100条并允许幂等归档")
  void 详情只返回最新100条并允许幂等归档() throws Exception {
    Session session = new Session("web:u:ops", "ops", "web", "u");
    session.setCreatedAt(LocalDateTime.of(2026, 9, 12, 9, 0));
    for (int i = 0; i < 101; i++) {
      session.append(ChatMessage.user("message-" + i));
    }
    when(sessionManager.get(session.getId())).thenReturn(Optional.of(session));

    mockMvc
        .perform(get("/api/v1/sessions/{id}", session.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.messages", hasSize(100)))
        .andExpect(jsonPath("$.data.messages[0].content").value("message-1"))
        .andExpect(jsonPath("$.data.messages[99].content").value("message-100"));

    mockMvc
        .perform(delete("/api/v1/sessions/{id}", session.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    verify(sessionManager).archive(session.getId());
  }
}
