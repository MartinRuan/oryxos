package com.oryxos.provider.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.config.ProviderProperties;
import com.oryxos.provider.impl.ProviderServiceImpl;
import com.oryxos.provider.mock.MockChatModel;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * LLM 调用审计落库集成测试.
 *
 * @author oryxos
 */
class LlmCallAuditIntegrationTest {

  private ProviderRegistry registry;
  private LlmCallRepository repository;
  private ProviderServiceImpl service;

  @BeforeEach
  void setUp() {
    registry = new ProviderRegistry();
    repository = mock(LlmCallRepository.class);
    when(repository.save(any(LlmCallEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ProviderProperties properties = new ProviderProperties();
    service = new ProviderServiceImpl(registry, properties, repository);

    ProviderDescriptor mockDescriptor =
        ProviderDescriptor.builder().name("mock").type("MOCK").defaultModel("mock-model").build();
    registry.register(mockDescriptor, new MockChatModel("Audited reply"));
  }

  @Test
  @DisplayName("验证每次模型调用同步落库 LlmCallEntity 审计数据")
  void testAuditRecordingOnCall() {
    ChatRequest request =
        ChatRequest.builder()
            .provider("mock")
            .message(ChatMessage.user("Audit test query"))
            .sessionId("session-audit-100")
            .build();

    ChatResponse response = service.call(request);

    assertThat(response).isNotNull();

    ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
    verify(repository, times(1)).save(captor.capture());

    LlmCallEntity saved = captor.getValue();
    assertThat(saved.getSessionId()).isEqualTo("session-audit-100");
    assertThat(saved.getProvider()).isEqualTo("mock");
    assertThat(saved.getModel()).isEqualTo("mock-model");
    assertThat(saved.getTotalTokens()).isEqualTo(30);
    assertThat(saved.getDurationMs()).isGreaterThanOrEqualTo(0);
    assertThat(saved.getCreatedAt()).isNotNull();
  }
}
