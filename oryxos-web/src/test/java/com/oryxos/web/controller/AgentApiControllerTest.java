package com.oryxos.web.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.AgentView;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.AgentLifecycleService;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.session.SessionManager;
import com.oryxos.provider.exception.ProviderErrorCode;
import com.oryxos.provider.exception.ProviderException;
import com.oryxos.web.exception.AgentInvocationTimeoutException;
import com.oryxos.web.exception.GlobalExceptionHandler;
import com.oryxos.web.service.AgentInvocationRunner;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentApiControllerTest {

  private ProfileRegistry profileRegistry;
  private SessionManager sessionManager;
  private AgentInvocationRunner invocationRunner;
  private AgentLifecycleService lifecycleService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    profileRegistry = mock(ProfileRegistry.class);
    sessionManager = mock(SessionManager.class);
    invocationRunner = mock(AgentInvocationRunner.class);
    lifecycleService = mock(AgentLifecycleService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AgentApiController(
                    profileRegistry, sessionManager, invocationRunner, lifecycleService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("成功调用只返回回复且每次使用不同临时会话")
  void 成功调用只返回回复且每次使用不同临时会话() throws Exception {
    Profile profile = new Profile();
    profile.setName("ops");
    when(profileRegistry.getRequiredProfile("ops")).thenReturn(profile);
    List<String> users = new ArrayList<>();
    when(sessionManager.getOrCreate(eq("invoke"), any(String.class), eq("ops")))
        .thenAnswer(
            invocation -> {
              String user = invocation.getArgument(1);
              users.add(user);
              return new Session("invoke:" + user + ":ops", "ops", "invoke", user);
            });
    when(invocationRunner.run(any(Session.class), eq("hello"))).thenReturn("reply");

    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              post("/api/v1/agents/ops/invoke")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.reply").value("reply"))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(
                          org.hamcrest.Matchers.containsString("sessionId"))));
    }

    assertNotEquals(users.get(0), users.get(1));
    verify(invocationRunner, times(2)).run(any(Session.class), eq("hello"));
  }

  @Test
  @DisplayName("未知Agent返回404")
  void 未知Agent返回404() throws Exception {
    when(profileRegistry.getRequiredProfile("missing"))
        .thenThrow(new OryxException(StandardErrorCode.PROFILE_NOT_FOUND, "missing"));

    invoke("missing").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
  }

  @Test
  @DisplayName("Provider不可用和调用超时保持503与504")
  void Provider不可用和调用超时保持503与504() throws Exception {
    Profile profile = new Profile();
    profile.setName("ops");
    Session session = new Session("invoke:one:ops", "ops", "invoke", "one");
    when(profileRegistry.getRequiredProfile("ops")).thenReturn(profile);
    when(sessionManager.getOrCreate(eq("invoke"), any(String.class), eq("ops")))
        .thenReturn(session);
    when(invocationRunner.run(session, "hello"))
        .thenThrow(
            new ProviderException(
                ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE,
                "deepseek",
                "deepseek-chat",
                "offline"))
        .thenThrow(new AgentInvocationTimeoutException());

    invoke("ops").andExpect(status().isServiceUnavailable());
    invoke("ops").andExpect(status().isGatewayTimeout());
  }

  @Test
  @DisplayName("CRUD与生成端点薄转发生命周期服务")
  void CRUD与生成端点薄转发生命周期服务() throws Exception {
    AgentView view =
        new AgentView(
            "ops",
            "Operations",
            "body",
            new AgentView.ProviderView("minimax", "MiniMax-M2.7", 0.2),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "agents/ops/AGENT.md",
            "---\nname: ops\n---\nbody");
    when(lifecycleService.generate("创建运维助手")).thenReturn(view.agentMarkdown());
    when(lifecycleService.create(eq("ops"), any(String.class))).thenReturn(view);
    when(lifecycleService.list()).thenReturn(List.of(view));
    when(lifecycleService.get("ops")).thenReturn(view);
    when(lifecycleService.update(eq("ops"), any(String.class))).thenReturn(view);

    mockMvc
        .perform(
            post("/api/v1/agents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sentence\":\"创建运维助手\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(view.agentMarkdown()));
    mockMvc
        .perform(
            post("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ops\",\"agentMarkdown\":\"---\\nname: ops\\n---\\nbody\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("ops"));
    mockMvc.perform(get("/api/v1/agents")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/agents/ops")).andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/v1/agents/ops")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentMarkdown\":\"---\\nname: ops\\n---\\nbody\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/agents/ops")).andExpect(status().isOk());

    verify(lifecycleService).delete("ops");
  }

  @Test
  @DisplayName("生成句子长度与创建模式冲突返回400")
  void 非法管理请求返回400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/agents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sentence\":\"\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"ops\",\"agentMarkdown\":\"raw\",\"description\":\"also structured\"}"))
        .andExpect(status().isBadRequest());
  }

  private org.springframework.test.web.servlet.ResultActions invoke(String name) throws Exception {
    return mockMvc.perform(
        post("/api/v1/agents/{name}/invoke", name)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"hello\"}"));
  }
}
