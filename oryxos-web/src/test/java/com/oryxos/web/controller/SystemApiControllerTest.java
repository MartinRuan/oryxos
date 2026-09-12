package com.oryxos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.SystemInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class SystemApiControllerTest {

  private SystemApiController controller;

  @BeforeEach
  void setUp() {
    ProviderRegistry registry = new ProviderRegistry();
    ProviderDescriptor descriptor =
        ProviderDescriptor.builder()
            .name("mock")
            .type("MOCK")
            .defaultModel("mock-model")
            .supportedModels(List.of("mock-model"))
            .apiKey("top-secret-api-key")
            .build();
    registry.register(descriptor, mock(ChatModel.class));
    controller = new SystemApiController(registry);
  }

  @Test
  @DisplayName("health返回UP和版本")
  void health返回UP和版本() {
    assertThat(controller.health().getData()).containsEntry("status", "UP");
    assertThat(controller.health().getData()).containsKey("version");
  }

  @Test
  @DisplayName("info返回显式Provider状态且绝不序列化API密钥")
  void info返回显式Provider状态且绝不序列化API密钥() throws Exception {
    ApiResponse<SystemInfo> response = controller.info();
    String json = new ObjectMapper().writeValueAsString(response);

    assertThat(response.getData().providers()).hasSize(1);
    assertThat(response.getData().providers().get(0).available()).isTrue();
    assertThat(json).contains("mock-model").doesNotContain("top-secret-api-key", "apiKey");
  }
}
