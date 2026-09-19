package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.config.AgentGenerationProperties;
import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.session.SessionManager;
import com.oryxos.provider.ProviderService;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class GenerateTest {

  @TempDir Path tempDir;
  private AgentStore store;
  private ProviderService provider;
  private SessionManager sessions;
  private AgentLifecycleService service;

  @BeforeEach
  void setUp() {
    store = new AgentStore(tempDir.resolve(".oryxos"));
    ProfileRegistry registry = new ProfileRegistry();
    AgentScheduler scheduler = mock(AgentScheduler.class);
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(registry), registry, scheduler, Set.of("minimax"), Set.of());
    provider = mock(ProviderService.class);
    sessions = mock(SessionManager.class);
    when(sessions.getOrCreate("agent-generation", "designer", "agent-generator"))
        .thenReturn(
            new Session(
                "agent-generation:designer:agent-generator",
                "agent-generator",
                "agent-generation",
                "designer"));
    service =
        new AgentLifecycleService(
            store,
            loader,
            registry,
            scheduler,
            provider,
            sessions,
            new AgentGenerationProperties());
  }

  @Test
  void 合法草稿通过校验但不落盘不注册() {
    String draft =
        "---\nname: generated\nprovider:\n  name: minimax\n  model: MiniMax-M2.7\n---\n任务正文";
    when(provider.chat(
            eq("agent-generation:designer:agent-generator"), any(Profile.class), any(String.class)))
        .thenReturn(ChatResponse.of(draft));

    assertThat(service.generate("创建测试助手")).isEqualTo(draft);
    assertThat(store.listDirectory("agents")).isEmpty();
    ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
    verify(provider)
        .chat(
            eq("agent-generation:designer:agent-generator"), profile.capture(), any(String.class));
    assertThat(profile.getValue().getProviderName()).isEqualTo("minimax");
    assertThat(profile.getValue().getModelName()).isEqualTo("MiniMax-M2.7");
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(provider)
        .chat(
            eq("agent-generation:designer:agent-generator"), any(Profile.class), prompt.capture());
    assertThat(prompt.getValue())
        .contains("identity 是包含 agent_name 和 prompt 的对象")
        .contains("settings 是包含 max_iterations 和 max_history_turns 的对象")
        .contains("不要输出代码围栏、<think>、推理过程或任何解释");
  }

  @Test
  void MiniMax推理前言和Markdown围栏会被提取为Agent文档() {
    String draft =
        "---\nname: generated\nprovider:\n  name: minimax\n  model: MiniMax-M2.7\n---\n任务正文";
    when(provider.chat(any(String.class), any(Profile.class), any(String.class)))
        .thenReturn(
            ChatResponse.of(
                "<think>planning\n---\nanalysis section\n---\n</think>\n```markdown\n"
                    + draft
                    + "\n```"));

    assertThat(service.generate("创建测试助手")).isEqualTo(draft);
    assertThat(store.listDirectory("agents")).isEmpty();
  }

  @Test
  void Provider异常沿既有审计调用路径透传() {
    OryxException unavailable =
        new OryxException(StandardErrorCode.SERVICE_UNAVAILABLE, "provider unavailable");
    doThrow(unavailable)
        .when(provider)
        .chat(any(String.class), any(Profile.class), any(String.class));

    assertThatThrownBy(() -> service.generate("创建测试助手")).isSameAs(unavailable);
  }

  @Test
  void 非法草稿返回40003() {
    when(provider.chat(any(String.class), any(Profile.class), any(String.class)))
        .thenReturn(ChatResponse.of("not an agent"));

    assertThatThrownBy(() -> service.generate("创建测试助手"))
        .isInstanceOfSatisfying(
            OryxException.class,
            error ->
                assertThat(error.getErrorCode())
                    .isEqualTo(StandardErrorCode.AGENT_GENERATION_INVALID));
  }
}
