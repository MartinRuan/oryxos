package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.profile.ProfileLoader;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.memory.LongTermMemory;
import com.oryxos.memory.MemoryScope;
import com.oryxos.memory.MemoryService;
import com.oryxos.storage.repository.LlmCallRepository;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * 第 28 节重启恢复集成验收.
 *
 * <p>连续创建两套独立 Spring 上下文，复用同一个 SQLite 和 MEMORY.md，验证 Profile、Schedule、Session、Memory 与审计数据均可在重启后恢复.
 *
 * @author OryxOS Team
 */
@Tag("integration")
class RestartRecoveryIT {

  private static final String PROFILE_NAME = "lesson28-restart-agent";
  private static final String SCHEDULE_ID = "lesson28-restart-schedule";
  private static final String SCHEDULE_MESSAGE = "执行重启恢复巡检";
  private static final String MEMORY_CONTENT = "lesson28-restart-memory";

  @TempDir Path tempDir;

  @Test
  void 重启后恢复Profile定时任务会话记忆和审计() throws IOException, SQLException {
    Path profileDirectory = tempDir.resolve("agents");
    Path memoryFile = tempDir.resolve("memory").resolve("MEMORY.md");
    Path databaseFile = tempDir.resolve("oryxos.db");
    Files.createDirectories(profileDirectory.resolve(PROFILE_NAME));
    Files.writeString(
        profileDirectory.resolve(PROFILE_NAME).resolve("AGENT.md"),
        agentDefinition(),
        StandardCharsets.UTF_8);
    createLegacyAuditTable(databaseFile);

    RestartTestConfiguration.profileDirectory = profileDirectory;
    RestartTestConfiguration.memoryFile = memoryFile;

    String sessionId = SessionManager.generateSessionId("scheduler", "scheduler", PROFILE_NAME);
    int firstRegistryIdentity;
    try (ConfigurableApplicationContext first = startContext(databaseFile)) {
      ProfileRegistry registry = first.getBean(ProfileRegistry.class);
      firstRegistryIdentity = System.identityHashCode(registry);
      Profile profile = registry.getRequiredProfile(PROFILE_NAME);
      assertScheduleRegistered(first);

      AgentScheduler scheduler = first.getBean(AgentScheduler.class);
      scheduler.runOnce(profile, profile.getSchedules().get(0));
      first.getBean(MemoryService.class).remember(MEMORY_CONTENT, MemoryScope.CORE);
      executeListDirectory(first, sessionId, "before-restart");

      assertThat(first.getBean(SessionManager.class).get(sessionId).orElseThrow().getMessages())
          .hasSize(2);
      assertThat(
              first.getBean(LlmCallRepository.class).findBySessionIdOrderByCreatedAtAsc(sessionId))
          .hasSize(1);
      assertThat(first.getBean(ToolInvocationRepository.class).findBySessionId(sessionId))
          .hasSize(1);
    }

    try (ConfigurableApplicationContext second = startContext(databaseFile)) {
      ProfileRegistry registry = second.getBean(ProfileRegistry.class);
      assertThat(System.identityHashCode(registry)).isNotEqualTo(firstRegistryIdentity);
      Profile profile = registry.getRequiredProfile(PROFILE_NAME);
      assertThat(profile.getSchedules())
          .singleElement()
          .satisfies(
              schedule -> {
                assertThat(schedule.getId()).isEqualTo(SCHEDULE_ID);
                assertThat(schedule.getMessage()).isEqualTo(SCHEDULE_MESSAGE);
              });
      assertScheduleRegistered(second);

      Session restored = second.getBean(SessionManager.class).get(sessionId).orElseThrow();
      assertThat(restored.getMessages()).hasSize(2);
      assertThat(second.getBean(MemoryService.class).load()).contains(MEMORY_CONTENT);
      assertThat(
              second.getBean(LlmCallRepository.class).findBySessionIdOrderByCreatedAtAsc(sessionId))
          .hasSize(1);
      assertThat(second.getBean(ToolInvocationRepository.class).findBySessionId(sessionId))
          .hasSize(1);

      second.getBean(AgentScheduler.class).runOnce(profile, profile.getSchedules().get(0));
      executeListDirectory(second, sessionId, "after-restart");

      Session continued = second.getBean(SessionManager.class).get(sessionId).orElseThrow();
      assertThat(continued.getId()).isEqualTo(sessionId);
      assertThat(continued.getMessages()).hasSize(4);
      assertThat(
              second.getBean(LlmCallRepository.class).findBySessionIdOrderByCreatedAtAsc(sessionId))
          .hasSize(2);
      assertThat(second.getBean(ToolInvocationRepository.class).findBySessionId(sessionId))
          .hasSize(2);
    } finally {
      RestartTestConfiguration.profileDirectory = null;
      RestartTestConfiguration.memoryFile = null;
    }
  }

  private ConfigurableApplicationContext startContext(Path databaseFile) {
    return new SpringApplicationBuilder(OryxApplication.class, RestartTestConfiguration.class)
        .web(WebApplicationType.NONE)
        .run(
            "--spring.datasource.url=jdbc:sqlite:" + databaseFile.toAbsolutePath(),
            "--spring.jpa.hibernate.ddl-auto=none",
            "--spring.sql.init.mode=always",
            "--spring.main.banner-mode=off",
            "--logging.level.root=WARN",
            "--oryxos.default-max-retries=0");
  }

  private static void assertScheduleRegistered(ConfigurableApplicationContext context) {
    TaskScheduler taskScheduler = context.getBean(TaskScheduler.class);
    verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
  }

  private static void executeListDirectory(
      ConfigurableApplicationContext context, String sessionId, String callId) {
    context
        .getBean(ToolExecutor.class)
        .execute(sessionId, new ToolCallIntent(callId, "list_dir", "{\"path\":\".\"}"));
  }

  private static void createLegacyAuditTable(Path databaseFile) throws SQLException {
    String legacyTable =
        """
        CREATE TABLE llm_calls (
          id VARCHAR(64) PRIMARY KEY,
          session_id VARCHAR(64) NOT NULL,
          provider VARCHAR(32) NOT NULL,
          model VARCHAR(64) NOT NULL,
          prompt_tokens INT NOT NULL DEFAULT 0,
          completion_tokens INT NOT NULL DEFAULT 0,
          total_tokens INT NOT NULL DEFAULT 0,
          duration_ms BIGINT NOT NULL DEFAULT 0,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """;
    try (Connection connection =
            DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        Statement statement = connection.createStatement()) {
      statement.execute(legacyTable);
    }
  }

  private static String agentDefinition() {
    return """
        ---
        name: lesson28-restart-agent
        description: 第28节重启恢复测试 Agent
        identity:
          agent_name: 重启恢复助手
          prompt: 执行重启恢复巡检
        provider:
          name: mock
          model: mock-model
          temperature: 0.0
        tools:
          - list_dir
        schedules:
          - id: lesson28-restart-schedule
            cron: "0 0 7 * * *"
            message: 执行重启恢复巡检
            timezone: Asia/Shanghai
        settings:
          max_iterations: 2
          max_history_turns: 20
        ---

        每次触发时执行巡检。
        """;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RestartTestConfiguration {

    private static Path profileDirectory;
    private static Path memoryFile;

    @Bean
    LongTermMemory restartLongTermMemory() {
      return new LongTermMemory(memoryFile);
    }

    @Bean
    @Primary
    TaskScheduler restartTaskScheduler() {
      return mock(TaskScheduler.class);
    }

    @Bean(name = "profileAutoLoader")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner restartProfileAutoLoader(ProfileLoader profileLoader) {
      return new ApplicationRunner() {
        @Override
        public void run(ApplicationArguments args) {
          profileLoader.loadProfiles(profileDirectory);
        }
      };
    }
  }
}
