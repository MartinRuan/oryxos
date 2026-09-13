package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.model.MessageType;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Profile.NotifyChannelConfig;
import com.oryxos.core.model.Profile.ScheduleConfig;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolDefinition;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.prompt.PromptBuilder;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.session.SessionManager;
import com.oryxos.provider.ProviderRegistry;
import com.oryxos.provider.mock.MockChatModel;
import com.oryxos.storage.entity.LlmCallEntity;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.SessionRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/**
 * 第 28 节定时触发、通知和多 Agent 隔离集成验收.
 *
 * <p>外部依赖由本地 HTTP 服务和可编排离线模型替代，其余路径使用生产 Bean 与 SQLite 审计表.
 *
 * @author OryxOS Team
 */
@Tag("integration")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite:/tmp/oryxos-scheduler-flow.db",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.sql.init.mode=always",
      "oryxos.default-max-retries=0"
    })
class SchedulerFlowIT {

  private static final String PROFILE_NAME = "lesson28-scheduler-agent";
  private static final String PROVIDER_NAME = "lesson28-scheduler";
  private static final String MODEL_NAME = "lesson28-model";
  private static final String SCHEDULE_ID = "lesson28-weather-notify";
  private static final String SCHEDULE_MESSAGE = "查询天气并推送穿衣建议";
  private static final String NOTIFY_CONTENT = "北京晴，25℃，建议穿薄外套。";

  @jakarta.annotation.Resource private AgentScheduler agentScheduler;
  @jakarta.annotation.Resource private ProfileRegistry profileRegistry;
  @jakarta.annotation.Resource private ProviderRegistry providerRegistry;
  @jakarta.annotation.Resource private PromptBuilder promptBuilder;
  @jakarta.annotation.Resource private SessionManager sessionManager;
  @jakarta.annotation.Resource private SessionRepository sessionRepository;
  @jakarta.annotation.Resource private LlmCallRepository llmCallRepository;
  @jakarta.annotation.Resource private ToolInvocationRepository toolInvocationRepository;

  private final AtomicInteger weatherRequestCount = new AtomicInteger();
  private final AtomicInteger notifyRequestCount = new AtomicInteger();
  private final AtomicReference<String> notifyPayload = new AtomicReference<>();
  private ScriptedChatModel scriptedChatModel;
  private HttpServer localServer;
  private String weatherUrl;
  private String notifyUrl;

  @BeforeEach
  void setUp() throws IOException {
    toolInvocationRepository.deleteAll();
    llmCallRepository.deleteAll();
    sessionRepository.deleteAll();
    profileRegistry.clear();

    weatherRequestCount.set(0);
    notifyRequestCount.set(0);
    notifyPayload.set(null);
    startLocalServer();

    scriptedChatModel = new ScriptedChatModel();
    registerProvider(PROVIDER_NAME, scriptedChatModel);
    profileRegistry.register(createScheduledProfile(PROFILE_NAME, PROVIDER_NAME, notifyUrl));
  }

  @AfterEach
  void tearDown() {
    if (localServer != null) {
      localServer.stop(0);
    }
  }

  @Test
  void 定时任务连续触发复用会话并精确记录三次模型两次工具() {
    scriptWeatherNotifyConversation("first");
    scriptWeatherNotifyConversation("second");
    Profile profile = profileRegistry.getRequiredProfile(PROFILE_NAME);
    ScheduleConfig schedule = profile.getSchedules().get(0);

    agentScheduler.runOnce(profile, schedule);
    agentScheduler.runOnce(profile, schedule);

    String sessionId = SessionManager.generateSessionId("scheduler", "scheduler", PROFILE_NAME);
    Session stored = sessionManager.get(sessionId).orElseThrow();
    assertThat(sessionRepository.findByProfileName(PROFILE_NAME)).hasSize(1);
    assertThat(stored.getMessages()).hasSize(12);
    assertThat(stored.getMessages())
        .extracting(message -> message.getRole())
        .containsExactly(
            MessageType.USER,
            MessageType.ASSISTANT,
            MessageType.TOOL,
            MessageType.ASSISTANT,
            MessageType.TOOL,
            MessageType.ASSISTANT,
            MessageType.USER,
            MessageType.ASSISTANT,
            MessageType.TOOL,
            MessageType.ASSISTANT,
            MessageType.TOOL,
            MessageType.ASSISTANT);

    List<LlmCallEntity> calls = llmCallRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    assertThat(calls)
        .hasSize(6)
        .allSatisfy(
            call -> {
              assertThat(call.isSuccess()).isTrue();
              assertThat(call.getSessionId()).isEqualTo(sessionId);
            });

    List<ToolInvocationEntity> tools = toolInvocationRepository.findBySessionId(sessionId);
    assertThat(tools).hasSize(4).allSatisfy(tool -> assertThat(tool.isSuccess()).isTrue());
    assertThat(tools)
        .extracting(ToolInvocationEntity::getToolName)
        .containsOnly("http_get", "notify");
    assertThat(weatherRequestCount).hasValue(2);
    assertThat(notifyRequestCount).hasValue(2);
    assertThat(notifyPayload.get()).contains(NOTIFY_CONTENT);
  }

  @Test
  void 通知被沙箱拒绝会写失败审计且下一次调度仍可成功() {
    Profile blockedProfile =
        createScheduledProfile(
            PROFILE_NAME, PROVIDER_NAME, "https://blocked.invalid/lesson28-webhook");
    profileRegistry.register(blockedProfile);
    scriptUntilNotify("blocked");

    agentScheduler.runOnce(blockedProfile, blockedProfile.getSchedules().get(0));

    String sessionId = SessionManager.generateSessionId("scheduler", "scheduler", PROFILE_NAME);
    assertThat(toolInvocationRepository.findBySessionId(sessionId))
        .hasSize(2)
        .anySatisfy(
            invocation -> {
              assertThat(invocation.getToolName()).isEqualTo("notify");
              assertThat(invocation.isSuccess()).isFalse();
              assertThat(invocation.getErrorMessage()).contains("域名不在白名单内");
            });

    Profile recoveredProfile = createScheduledProfile(PROFILE_NAME, PROVIDER_NAME, notifyUrl);
    profileRegistry.register(recoveredProfile);
    scriptWeatherNotifyConversation("recovered");
    agentScheduler.runOnce(recoveredProfile, recoveredProfile.getSchedules().get(0));

    assertThat(sessionManager.get(sessionId).orElseThrow().getMessages()).hasSize(6);
    assertThat(llmCallRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).hasSize(5);
    assertThat(toolInvocationRepository.findBySessionId(sessionId))
        .hasSize(4)
        .filteredOn(ToolInvocationEntity::isSuccess)
        .hasSize(3);
    assertThat(notifyRequestCount).hasValue(1);
  }

  @Test
  void 多Agent的工具会话和调度锁相互隔离() {
    String providerA = "lesson28-agent-a";
    String providerB = "lesson28-agent-b";
    ScriptedChatModel failedModel = new ScriptedChatModel();
    failedModel.fail(new IllegalStateException("Agent A 调度失败"));
    registerProvider(providerA, failedModel);
    registerProvider(providerB, new MockChatModel("B 完成"));
    Profile profileA = createIsolationProfile("lesson28-a", providerA, "read_file", "schedule-a");
    Profile profileB = createIsolationProfile("lesson28-b", providerB, "http_get", "schedule-b");
    profileRegistry.register(profileA);
    profileRegistry.register(profileB);

    Session promptSessionA = new Session("prompt-a", profileA.getName(), "scheduler", "scheduler");
    Session promptSessionB = new Session("prompt-b", profileB.getName(), "scheduler", "scheduler");
    assertThat(promptBuilder.build(promptSessionA, profileA).getTools())
        .extracting(ToolDefinition::getName)
        .containsExactly("read_file");
    assertThat(promptBuilder.build(promptSessionB, profileB).getTools())
        .extracting(ToolDefinition::getName)
        .containsExactly("http_get");

    agentScheduler.runOnce(profileA, profileA.getSchedules().get(0));
    agentScheduler.runOnce(profileB, profileB.getSchedules().get(0));

    String sessionA =
        SessionManager.generateSessionId("scheduler", "scheduler", profileA.getName());
    String sessionB =
        SessionManager.generateSessionId("scheduler", "scheduler", profileB.getName());
    assertThat(sessionA).isNotEqualTo(sessionB);
    assertThat(sessionManager.get(sessionA).orElseThrow().getMessages()).isEmpty();
    assertThat(sessionManager.get(sessionB).orElseThrow().getMessages()).hasSize(2);
    assertThat(llmCallRepository.findBySessionIdOrderByCreatedAtAsc(sessionA))
        .singleElement()
        .satisfies(call -> assertThat(call.isSuccess()).isFalse());
    assertThat(llmCallRepository.findBySessionIdOrderByCreatedAtAsc(sessionB))
        .singleElement()
        .satisfies(call -> assertThat(call.isSuccess()).isTrue());
    assertThat(agentScheduler.lockFor("schedule-a"))
        .isNotSameAs(agentScheduler.lockFor("schedule-b"));
  }

  private void startLocalServer() throws IOException {
    localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    localServer.createContext(
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
    localServer.createContext(
        "/notify",
        exchange -> {
          notifyRequestCount.incrementAndGet();
          notifyPayload.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().close();
        });
    localServer.start();
    int port = localServer.getAddress().getPort();
    weatherUrl = "http://127.0.0.1:" + port + "/weather";
    notifyUrl = "http://127.0.0.1:" + port + "/notify";
  }

  private void scriptWeatherNotifyConversation(String suffix) {
    scriptUntilNotify(suffix);
    scriptedChatModel.reply("定时任务已完成", List.of());
  }

  private void scriptUntilNotify(String suffix) {
    scriptedChatModel.reply(
        "正在查询天气",
        List.of(
            new ToolCallIntent(
                "weather-" + suffix, "http_get", "{\"url\":\"" + weatherUrl + "\"}")));
    scriptedChatModel.reply(
        "正在发送通知",
        List.of(
            new ToolCallIntent(
                "notify-" + suffix,
                "notify",
                "{\"content\":\"" + NOTIFY_CONTENT + "\",\"channel\":\"local\"}")));
  }

  private void registerProvider(String name, ChatModel chatModel) {
    ProviderDescriptor descriptor =
        ProviderDescriptor.builder()
            .name(name)
            .type("MOCK")
            .defaultModel(MODEL_NAME)
            .supportedModels(List.of(MODEL_NAME))
            .build();
    providerRegistry.register(descriptor, chatModel);
  }

  private static Profile createScheduledProfile(
      String profileName, String providerName, String webhookUrl) {
    NotifyChannelConfig notifyChannel =
        new NotifyChannelConfig("local", "webhook", Map.of("url", webhookUrl));
    ScheduleConfig schedule = new ScheduleConfig("0 0 8 * * *", SCHEDULE_MESSAGE, "Asia/Shanghai");
    schedule.setId(SCHEDULE_ID);
    return Profile.builder()
        .name(profileName)
        .description("第28节定时链路集成验收 Agent")
        .identity(new Profile.Identity("定时验收助手", "查询天气并推送通知"))
        .provider(new Profile.ProviderConfig(providerName, MODEL_NAME, 0.0))
        .tools(List.of("http_get", "notify"))
        .notifyChannels(List.of(notifyChannel))
        .schedules(List.of(schedule))
        .settings(new Profile.Settings(4, 30))
        .build();
  }

  private static Profile createIsolationProfile(
      String profileName, String providerName, String toolName, String scheduleId) {
    ScheduleConfig schedule = new ScheduleConfig("0 0 9 * * *", "执行隔离测试", "Asia/Shanghai");
    schedule.setId(scheduleId);
    return Profile.builder()
        .name(profileName)
        .identity(new Profile.Identity(profileName, "只执行自己的任务"))
        .provider(new Profile.ProviderConfig(providerName, MODEL_NAME, 0.0))
        .tools(List.of(toolName))
        .schedules(List.of(schedule))
        .settings(new Profile.Settings(2, 20))
        .build();
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
      assertThat(steps).as("scripted scheduler model response queue").isNotEmpty();
      return steps.removeFirst().apply(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
      return new MockChatModel().getDefaultOptions();
    }
  }
}
