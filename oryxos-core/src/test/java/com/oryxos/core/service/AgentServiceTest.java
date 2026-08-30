package com.oryxos.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.service.impl.AgentServiceImpl;
import com.oryxos.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

  @Mock private ProfileRegistry profileRegistry;
  @Mock private ReActLoop reActLoop;
  @Mock private SessionManager sessionManager;

  private AgentService agentService;
  private Profile profile;
  private Session session;

  @BeforeEach
  void setUp() {
    agentService = new AgentServiceImpl(profileRegistry, reActLoop, sessionManager);

    profile = Profile.builder().name("ops-agent").build();
    session = new Session("sess-agent-01", "ops-agent", "feishu", "user-admin");
    when(profileRegistry.getRequiredProfile("ops-agent")).thenReturn(profile);
  }

  @Test
  @DisplayName("全流程跑通：绑定 Context、驱动 ReActLoop、保存 Session 并在结束后清空 Context")
  void 全流程跑通_调用ReActLoop并保存Session() {
    when(reActLoop.run(session, "检查集群状态", profile)).thenReturn("集群健康状态良好");

    String result = agentService.process(session, "检查集群状态");

    assertEquals("集群健康状态良好", result);
    verify(sessionManager).save(session);
    assertNull(ProfileContext.get(), "ProfileContext 必须在正常结束后被彻底清空");
  }

  @Test
  @DisplayName("当 ReActLoop 发生异常时，ProfileContext 在 finally 中必须被彻底清空")
  void ThreadLocal_在异常时必清空() {
    when(reActLoop.run(session, "故障模拟", profile)).thenThrow(new IllegalStateException("LLM 连接中断"));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> agentService.process(session, "故障模拟"));

    assertEquals("LLM 连接中断", ex.getMessage());
    assertNull(ProfileContext.get(), "ProfileContext 必须在异常情况下也被彻底清空，防止线程池污染");
  }
}
