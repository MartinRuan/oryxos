package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.MessageType;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;
import com.oryxos.core.profile.ProfileLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.impl.ToolExecutorImpl;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.mock.MockChatModel;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.SessionRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.tool.sandbox.SandboxViolationException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 第 27 节人触发全流程集成验收.
 *
 * <p>使用可编排的离线 ChatModel 与本地 HTTP 服务隔离外部依赖，其余路径均走真实生产 Bean：
 * SessionManager、AgentService、ReActLoop、ProviderService、ToolExecutor、Sandbox 和三张 SQLite 表.
 *
 * @author OryxOS Team
 */
@Tag("integration")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite:/tmp/oryxos-human-trigger-flow.db",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.sql.init.mode=always",
      "oryxos.default-max-retries=0"
    })
@AutoConfigureMockMvc
class HumanTriggerFlowIT {

  private static final String PROFILE_NAME = "lesson27-agent";
  private static final String PROVIDER_NAME = "lesson27";
  private static final String MODEL_NAME = "lesson27-model";
  private static final String WEATHER_QUESTION = "今天北京天气怎么样，穿什么合适";
  private static final String WEATHER_REPLY = "北京今天晴，25℃，建议穿薄外套。";
  private static final Path WORKSPACE_ROOT =
      Files.isRegularFile(Path.of(".oryxos", "AGENTS.md")) ? Path.of(".") : Path.of("..");
  private static final Path RUNTIME_ROOT = WORKSPACE_ROOT.resolve(".oryxos").normalize();
  private static final Path MEMORY_PATH = RUNTIME_ROOT.resolve("memory").resolve("MEMORY.md");

  @jakarta.annotation.Resource private MockMvc mockMvc;
  @jakarta.annotation.Resource private ObjectMapper objectMapper;
  @jakarta.annotation.Resource private SessionManager sessionManager;
  @jakarta.annotation.Resource private AgentService agentService;
  @jakarta.annotation.Resource private ProfileLoader profileLoader;
  @jakarta.annotation.Resource private ProfileRegistry profileRegistry;
  @jakarta.annotation.Resource private ProviderRegistry providerRegistry;
  @jakarta.annotation.Resource private ToolExecutor toolExecutor;
  @jakarta.annotation.Resource private ToolRegistry toolRegistry;
  @jakarta.annotation.Resource private SessionRepository sessionRepository;
  @jakarta.annotation.Resource private LlmCallRepository llmCallRepository;
  @jakarta.annotation.Resource private ToolInvocationRepository toolInvocationRepository;

  private final AtomicInteger weatherRequestCount = new AtomicInteger();
  private ScriptedChatModel scriptedChatModel;
  private HttpServer weatherServer;
  private String weatherUrl;

  @BeforeEach
  void setUp() throws IOException {
    toolInvocationRepository.deleteAll();
    llmCallRepository.deleteAll();
    sessionRepository.deleteAll();

    profileRegistry.clear();
    profileRegistry.register(createProfile());
    scriptedChatModel = new ScriptedChatModel();
    registerScriptedProvider();

    weatherRequestCount.set(0);
    weatherServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    weatherServer.createContext(
        "/weather",
        exchange -> {
          weatherRequestCount.incrementAndGet();
          byte[] body =
              "{\"city\":\"北京\",\"temperature\":25,\"condition\":\"晴\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    weatherServer.start();
    weatherUrl = "http://127.0.0.1:" + weatherServer.getAddress().getPort() + "/weather";
  }

  @AfterEach
  void tearDown() {
    if (weatherServer != null) {
      weatherServer.stop(0);
    }
  }

  @Test
  void Cli入口完成两轮模型一次工具并在三张表精确对账() throws Exception {
    assertRuntimeAssembly();
    String memoryBefore = readMemory();
    scriptWeatherConversation();

    Session session = sessionManager.getOrCreate("cli", "lesson27-cli-user", PROFILE_NAME);
    String reply = agentService.process(session, WEATHER_QUESTION);

    assertThat(session.getId())
        .isEqualTo(SessionManager.generateSessionId("cli", "lesson27-cli-user", PROFILE_NAME));
    assertThat(reply).isEqualTo(WEATHER_REPLY);
    assertConversationAndAudit(session.getId());
    assertThat(readMemory()).isEqualTo(memoryBefore);
    assertThat(ProfileContext.current()).isNull();

    mockMvc
        .perform(get("/api/v1/sessions/{id}", session.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(session.getId()))
        .andExpect(jsonPath("$.data.messageCount").value(4))
        .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
        .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
        .andExpect(jsonPath("$.data.messages[2].role").value("TOOL"))
        .andExpect(jsonPath("$.data.messages[3].content").value(WEATHER_REPLY));
  }

  @Test
  void Rest入口复用同一执行链并返回可查询的完整会话() throws Exception {
    String memoryBefore = readMemory();
    scriptWeatherConversation();

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"profileName\":\""
                            + PROFILE_NAME
                            + "\",\"userId\":\"lesson27-web-user\"}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
    String sessionId = createJson.path("data").path("id").asText();

    mockMvc
        .perform(
            post("/api/v1/sessions/{id}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + WEATHER_QUESTION + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value(sessionId))
        .andExpect(jsonPath("$.data.reply").value(WEATHER_REPLY));

    assertThat(sessionId)
        .isEqualTo(SessionManager.generateSessionId("web", "lesson27-web-user", PROFILE_NAME));
    assertConversationAndAudit(sessionId);
    assertThat(readMemory()).isEqualTo(memoryBefore);

    mockMvc
        .perform(get("/api/v1/sessions/{id}", sessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.channel").value("web"))
        .andExpect(jsonPath("$.data.messageCount").value(4))
        .andExpect(jsonPath("$.data.messages[0].content").value(WEATHER_QUESTION))
        .andExpect(
            jsonPath("$.data.messages[2].content")
                .value(org.hamcrest.Matchers.containsString("25")))
        .andExpect(jsonPath("$.data.messages[3].content").value(WEATHER_REPLY));
  }

  @Test
  void Provider沙箱与工具异常均落失败审计且后续会话可继续() {
    scriptedChatModel.fail(new IllegalStateException("provider exploded"));
    Session providerFailure =
        sessionManager.getOrCreate("cli", "provider-failure-user", PROFILE_NAME);

    assertThatThrownBy(() -> agentService.process(providerFailure, WEATHER_QUESTION))
        .hasMessageContaining("provider exploded");
    List<LlmCallEntity> failedCalls =
        llmCallRepository.findBySessionIdOrderByCreatedAtAsc(providerFailure.getId());
    assertThat(failedCalls)
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.isSuccess()).isFalse();
              assertThat(call.getErrorMessage()).contains("provider exploded");
            });

    ToolCallIntent deniedCall =
        new ToolCallIntent(
            "call-denied", "http_get", "{\"url\":\"https://blocked.invalid/weather\"}");
    assertThatThrownBy(() -> toolExecutor.execute("sandbox-failure-session", deniedCall))
        .isInstanceOf(SandboxViolationException.class)
        .hasMessageContaining("域名不在白名单内");
    assertFailedToolAudit("sandbox-failure-session", "http_get", "域名不在白名单内");

    ToolExecutorImpl executor = (ToolExecutorImpl) toolExecutor;
    executor.registerTool(createThrowingTool());
    ToolCallIntent brokenCall =
        new ToolCallIntent("call-broken", "lesson27_broken", "{\"input\":\"boom\"}");
    assertThatThrownBy(() -> executor.execute("tool-failure-session", brokenCall))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tool exploded");
    assertFailedToolAudit("tool-failure-session", "lesson27_broken", "tool exploded");

    scriptedChatModel = new ScriptedChatModel();
    registerScriptedProvider();
    scriptWeatherConversation();
    Session recovered = sessionManager.getOrCreate("cli", "recovered-user", PROFILE_NAME);
    assertThat(agentService.process(recovered, WEATHER_QUESTION)).isEqualTo(WEATHER_REPLY);
    assertConversationAndAudit(recovered.getId());
  }

  private void assertRuntimeAssembly() {
    assertThat(sessionRepository).isNotNull();
    assertThat(llmCallRepository).isNotNull();
    assertThat(toolInvocationRepository).isNotNull();
    assertThat(profileRegistry.getRequiredProfile(PROFILE_NAME)).isNotNull();
    profileLoader.loadProfiles(RUNTIME_ROOT.resolve("agents"));
    assertThat(profileRegistry.listProfiles())
        .extracting(Profile::getName)
        .contains("ops-agent", "kimi-agent", "minimax-agent");
    assertThat(List.of("AGENTS.md", "SOUL.md", "USER.md"))
        .allSatisfy(file -> assertThat(RUNTIME_ROOT.resolve(file)).isRegularFile());
    assertThat(toolRegistry.getAllTools())
        .extracting(OryxTool::getName)
        .containsExactlyInAnyOrder(
            "read_file",
            "write_file",
            "list_dir",
            "shell",
            "http_get",
            "http_post",
            "save_memory",
            "recall_memory",
            "notify");
  }

  private void assertConversationAndAudit(String sessionId) {
    Session stored = sessionManager.get(sessionId).orElseThrow();
    assertThat(stored.getMessages())
        .extracting(message -> message.getRole())
        .containsExactly(
            MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT);
    assertThat(stored.getMessages().get(1).getToolCalls())
        .singleElement()
        .extracting(ToolCallIntent::getName)
        .isEqualTo("http_get");
    assertThat(stored.getMessages().get(2).getContent()).contains("北京", "25", "晴");

    List<LlmCallEntity> calls = llmCallRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    assertThat(calls)
        .hasSize(2)
        .allSatisfy(
            call -> {
              assertThat(call.getSessionId()).isEqualTo(sessionId);
              assertThat(call.isSuccess()).isTrue();
              assertThat(call.getPromptTokens()).isPositive();
              assertThat(call.getCompletionTokens()).isPositive();
              assertThat(call.getTotalTokens()).isPositive();
            });

    List<ToolInvocationEntity> invocations = toolInvocationRepository.findBySessionId(sessionId);
    assertThat(invocations)
        .singleElement()
        .satisfies(
            invocation -> {
              assertThat(invocation.getToolName()).isEqualTo("http_get");
              assertThat(invocation.isSuccess()).isTrue();
              assertThat(invocation.getResultJson()).contains("北京", "25", "晴");
              assertThat(invocation.getDurationMs()).isNotNegative();
            });
    assertThat(weatherRequestCount).hasValue(1);
  }

  private void assertFailedToolAudit(String sessionId, String toolName, String errorMessage) {
    assertThat(toolInvocationRepository.findBySessionId(sessionId))
        .singleElement()
        .satisfies(
            invocation -> {
              assertThat(invocation.getToolName()).isEqualTo(toolName);
              assertThat(invocation.isSuccess()).isFalse();
              assertThat(invocation.getErrorMessage()).contains(errorMessage);
            });
  }

  private void scriptWeatherConversation() {
    scriptedChatModel.reply(
        "正在查询北京天气",
        List.of(
            new ToolCallIntent("call-weather-1", "http_get", "{\"url\":\"" + weatherUrl + "\"}")));
    scriptedChatModel.reply(WEATHER_REPLY, List.of());
  }

  private void registerScriptedProvider() {
    ProviderDescriptor descriptor =
        ProviderDescriptor.builder()
            .name(PROVIDER_NAME)
            .type("MOCK")
            .defaultModel(MODEL_NAME)
            .supportedModels(List.of(MODEL_NAME))
            .build();
    providerRegistry.register(descriptor, scriptedChatModel);
  }

  private static Profile createProfile() {
    Profile profile = new Profile();
    profile.setName(PROFILE_NAME);
    profile.setDescription("第27节人触发集成验收 Agent");
    profile.setIdentity(new Profile.Identity("集成验收助手", "查询天气并给出穿衣建议"));
    profile.setProvider(new Profile.ProviderConfig(PROVIDER_NAME, MODEL_NAME, 0.0));
    profile.setTools(List.of("http_get"));
    profile.setBootstrap(
        List.of(
            RUNTIME_ROOT.resolve("AGENTS.md").toString(),
            RUNTIME_ROOT.resolve("SOUL.md").toString(),
            RUNTIME_ROOT.resolve("USER.md").toString()));
    profile.setSettings(new Profile.Settings(3, 20));
    return profile;
  }

  private static OryxTool createThrowingTool() {
    return new OryxTool() {
      @Override
      public String getName() {
        return "lesson27_broken";
      }

      @Override
      public String getDescription() {
        return "第27节异常审计测试工具";
      }

      @Override
      public String getInputSchema() {
        return "{\"type\":\"object\"}";
      }

      @Override
      public ToolResult execute(String inputJson) {
        throw new IllegalStateException("tool exploded");
      }
    };
  }

  private static String readMemory() throws IOException {
    return Files.exists(MEMORY_PATH) ? Files.readString(MEMORY_PATH) : "";
  }

  private static final class ScriptedChatModel implements ChatModel {

    private final Deque<Function<Prompt, org.springframework.ai.chat.model.ChatResponse>> steps =
        new ArrayDeque<>();

    void reply(String content, List<ToolCallIntent> toolCalls) {
      steps.add(prompt -> new MockChatModel(content, toolCalls).call(prompt));
    }

    void fail(RuntimeException exception) {
      steps.add(
          prompt -> {
            throw exception;
          });
    }

    @Override
    public synchronized org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
      assertThat(steps).as("scripted model response queue").isNotEmpty();
      return steps.removeFirst().apply(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
      return new MockChatModel().getDefaultOptions();
    }
  }
}
