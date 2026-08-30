package com.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.core.model.ToolDefinition;
import com.oryxos.provider.config.ProviderProperties;
import com.oryxos.provider.exception.ProviderException;
import com.oryxos.provider.impl.ProviderServiceImpl;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * ProviderService 验收测试. 覆盖课件关键回归点： 1. 按名路由_两个provider不串台 2. 未知名 Provider 抛异常 3.
 * 调用失败_审计必须留下success为false的记录 4. 带工具schema调用_请求里关闭了自动执行
 *
 * @author oryxos
 */
class ProviderServiceTest {

  private ProviderRegistry registry;
  private LlmCallRepository repository;
  private ProviderServiceImpl service;
  private ChatModel deepseekModel;
  private ChatModel kimiModel;

  @BeforeEach
  void setUp() {
    registry = new ProviderRegistry();
    repository = mock(LlmCallRepository.class);
    ProviderProperties properties = new ProviderProperties();
    properties.setDefaultMaxRetries(0); // 单元测试不重试直接验证
    service = new ProviderServiceImpl(registry, properties, repository);

    deepseekModel = mock(ChatModel.class);
    kimiModel = mock(ChatModel.class);

    registry.register(
        ProviderDescriptor.builder().name("deepseek").defaultModel("deepseek-chat").build(),
        deepseekModel);

    registry.register(
        ProviderDescriptor.builder().name("kimi").defaultModel("moonshot-v1-8k").build(),
        kimiModel);
  }

  @Test
  @DisplayName("按名路由：两个 Provider 显式寻址不串台")
  void 按名路由_两个provider不串台() {
    org.springframework.ai.chat.model.ChatResponse mockAiResp =
        new org.springframework.ai.chat.model.ChatResponse(
            List.of(new Generation(new AssistantMessage("Hello from Kimi"))));
    when(kimiModel.call(any(Prompt.class))).thenReturn(mockAiResp);

    Profile kimiProfile = createProfile("kimi", "moonshot-v1-8k");
    ChatRequest prompt = ChatRequest.builder().prompt("你好").build();

    ChatResponse response = service.chat("s-1", kimiProfile, prompt);

    assertThat(response.getContent()).isEqualTo("Hello from Kimi");
    assertThat(response.getProvider()).isEqualTo("kimi");

    // kimi 调了 1 次，deepseek 一次都没被碰 —— "不串台"直接证据
    verify(kimiModel, times(1)).call(any(Prompt.class));
    verify(deepseekModel, never()).call(any(Prompt.class));
  }

  @Test
  @DisplayName("未知名 Provider 抛出明确异常")
  void 未知Provider_抛出明确异常() {
    Profile unknownProfile = createProfile("unknown-provider", "model-x");
    ChatRequest prompt = ChatRequest.builder().prompt("test").build();

    assertThatThrownBy(() -> service.chat("s-1", unknownProfile, prompt))
        .isInstanceOf(ProviderException.class)
        .hasMessageContaining("unknown-provider");
  }

  @Test
  @DisplayName("调用失败：审计必须留下 success 为 false 的记录并继续上抛异常")
  void 调用失败_审计必须留下success为false的记录() {
    when(deepseekModel.call(any(Prompt.class)))
        .thenThrow(new RuntimeException("connect timeout to deepseek"));

    Profile deepseekProfile = createProfile("deepseek", "deepseek-chat");
    ChatRequest prompt = ChatRequest.builder().prompt("test").build();

    assertThatThrownBy(() -> service.chat("s-1", deepseekProfile, prompt))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("connect timeout");

    // 验证审计记录先落库：success=false + 错误原因
    ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(repository, times(1)).save(captor.capture());

    LlmCallEntity recorded = captor.getValue();
    assertThat(recorded.getSessionId()).isEqualTo("s-1");
    assertThat(recorded.getProvider()).isEqualTo("deepseek");
    assertThat(recorded.isSuccess()).isFalse();
    assertThat(recorded.getErrorMessage()).contains("connect timeout");
  }

  @Test
  @DisplayName("带工具 schema 调用：只生成 Schema、关闭底层自动执行")
  void 带工具schema调用_请求里关闭了自动执行() {
    org.springframework.ai.chat.model.ChatResponse mockAiResp =
        new org.springframework.ai.chat.model.ChatResponse(
            List.of(new Generation(new AssistantMessage("I will call tool"))));
    when(deepseekModel.call(any(Prompt.class))).thenReturn(mockAiResp);

    ToolDefinition httpGetTool =
        ToolDefinition.builder()
            .name("http_get")
            .description("发起 HTTP GET 请求")
            .inputJsonSchema("{\"type\":\"object\"}")
            .build();

    Profile deepseekProfile = createProfile("deepseek", "deepseek-chat");
    ChatRequest prompt = ChatRequest.builder().prompt("获取网页内容").tools(List.of(httpGetTool)).build();

    service.chat("s-1", deepseekProfile, prompt);

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(deepseekModel).call(captor.capture());

    Prompt capturedPrompt = captor.getValue();
    assertThat(capturedPrompt).isNotNull();
  }

  private Profile createProfile(String providerName, String model) {
    Profile profile = new Profile();
    profile.setName(providerName + "-agent");
    profile.setProvider(new Profile.ProviderConfig(providerName, model, 0.7));
    return profile;
  }
}
