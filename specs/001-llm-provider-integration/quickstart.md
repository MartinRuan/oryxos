# Phase 1 Quickstart Guide: US-1 LLM Provider 对接与显式路由

**Feature**: `001-llm-provider-integration`
**Date**: 2026-08-29

---

## 1. 验证目标

通过单元测试与集成测试，验证以下核心场景：
1. **显式路由**：调用 `qwen`、`deepseek` 与 `mock` 正确分发到对应底层模型。
2. **默认模型解析**：省略 `model` 参数时自动回退为 Provider 的 `default_model`。
3. **Function Calling 协议适配**：携带 `ToolDefinition` 时生成标准 JSON Schema，并准确提取模型返回的 `ToolCallIntent`。
4. **审计持久化**：每次模型调用完成后 `llm_calls` 审计记录同步生成。
5. **脱机验证**：在无网络、无 API Key 的 CI 环境下，使用内置 `mock` Provider 毫秒级通过全量测试。

---

## 2. 离线快速验证场景 (Mock Provider)

```java
@SpringBootTest
class ProviderServiceIntegrationTest {

  @Autowired
  private ProviderService providerService;

  @Test
  @DisplayName("调用 Mock Provider 验证同步文本生成与审计记录生成")
  void testMockProviderCall() {
    ChatRequest request = ChatRequest.builder()
        .provider("mock")
        .message(ChatMessage.user("Hello OryxOS"))
        .sessionId("session-test-001")
        .build();

    ChatResponse response = providerService.call(request);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getUsage().getTotalTokens()).isPositive();
    assertThat(response.getDurationMs()).isPositive();
  }

  @Test
  @DisplayName("验证 Function Calling 工具意图解析（禁止自动执行）")
  void testFunctionCallingIntentExtraction() {
    ToolDefinition tool = ToolDefinition.builder()
        .name("read_file")
        .description("Read file content by path")
        .inputJsonSchema("{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}")
        .build();

    ChatRequest request = ChatRequest.builder()
        .provider("mock")
        .message(ChatMessage.user("Please read file /etc/hosts"))
        .tool(tool)
        .build();

    ChatResponse response = providerService.call(request);

    assertThat(response.getToolCalls()).isNotEmpty();
    assertThat(response.getToolCalls().get(0).getName()).isEqualTo("read_file");
  }
}
```

---

## 3. 自动化测试验证指令

```bash
# 1. 格式检查与修正
mvn spotless:apply

# 2. 全量模块构建、质量门禁与测试
mvn clean verify
```
