package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.model.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProfileLoader 验收测试. 覆盖：合法 YAML 全字段解析、引用不存在 Provider 报错、坏文件不阻断其余加载、${ENV} 占位解析.
 *
 * @author oryxos
 */
class ProfileLoaderTest {

  private final ProfileLoader loader = new ProfileLoader(new ProfileRegistry());

  @Test
  @DisplayName("合法 YAML 全字段解析：所有元数据正确映射到 Profile 对象")
  void 合法YAML全字段解析_字段完整映射() {
    String yaml =
        """
        name: ops-agent
        description: 运维智能助手
        identity:
          agent_name: 运维小欧
          prompt: 你是一个专业的 Linux 运维助手
        provider:
          name: deepseek
          model: deepseek-chat
          temperature: 0.7
          api_key: sk-test-key
          base_url: https://api.deepseek.com
        tools:
          - read_file
          - shell
          - http_get
        skills:
          - git-ops
          - log-analysis
        mcp_servers:
          - github-mcp
        channels:
          - name: cli
            type: stdio
        notify_channels:
          - ops-feishu-webhook
        schedules:
          - cron: "0 0 9 * * ?"
            message: "每天上午 9 点巡检集群健康状态"
            timezone: "Asia/Shanghai"
        bootstrap:
          - AGENTS.md
          - SOUL.md
          - USER.md
        settings:
          max_iterations: 15
          max_history_turns: 30
        """;

    Profile profile = loader.parse(yaml);

    assertThat(profile.getName()).isEqualTo("ops-agent");
    assertThat(profile.getDescription()).isEqualTo("运维智能助手");

    // identity
    assertThat(profile.getIdentity()).isNotNull();
    assertThat(profile.getIdentity().getAgentName()).isEqualTo("运维小欧");
    assertThat(profile.getIdentity().getPrompt()).isEqualTo("你是一个专业的 Linux 运维助手");

    // provider
    assertThat(profile.getProvider()).isNotNull();
    assertThat(profile.getProvider().getName()).isEqualTo("deepseek");
    assertThat(profile.getProvider().getModel()).isEqualTo("deepseek-chat");
    assertThat(profile.getProvider().getTemperature()).isEqualTo(0.7);
    assertThat(profile.getProvider().getApiKey()).isEqualTo("sk-test-key");
    assertThat(profile.getProvider().getBaseUrl()).isEqualTo("https://api.deepseek.com");

    // lists
    assertThat(profile.getTools()).containsExactly("read_file", "shell", "http_get");
    assertThat(profile.getSkills()).containsExactly("git-ops", "log-analysis");
    assertThat(profile.getMcpServers()).containsExactly("github-mcp");
    assertThat(profile.getNotifyChannels()).containsExactly("ops-feishu-webhook");
    assertThat(profile.getBootstrap()).containsExactly("AGENTS.md", "SOUL.md", "USER.md");

    // channels & schedules
    assertThat(profile.getChannels()).hasSize(1);
    assertThat(profile.getChannels().get(0).getName()).isEqualTo("cli");
    assertThat(profile.getSchedules()).hasSize(1);
    assertThat(profile.getSchedules().get(0).getCron()).isEqualTo("0 0 9 * * ?");
    assertThat(profile.getSchedules().get(0).getMessage()).contains("巡检集群");

    // settings
    assertThat(profile.getSettings().getMaxIterations()).isEqualTo(15);
    assertThat(profile.getSettings().getMaxHistoryTurns()).isEqualTo(30);
  }

  @Test
  @DisplayName("引用不存在的 Provider 时校验报错清晰")
  void 引用不存在的provider_报错清晰() {
    String yaml =
        """
        name: test-agent
        provider:
          name: unknown-cloud-llm
          model: gpt-x
        """;

    Set<String> configuredProviders = Set.of("deepseek", "qwen", "kimi");

    assertThatThrownBy(() -> loader.parse(yaml, configuredProviders))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("unknown-cloud-llm")
        .hasMessageContaining("deepseek");
  }

  @Test
  @DisplayName("坏文件不阻断其余合法 Profile 加载")
  void 坏文件不阻断其余加载(@TempDir Path tempDir) throws IOException {
    // 1. 合法 Profile 1
    Files.writeString(
        tempDir.resolve("agent1.yaml"),
        """
        name: agent-one
        provider:
          name: deepseek
          model: deepseek-chat
        """);

    // 2. 损坏 YAML (语法错误)
    Files.writeString(
        tempDir.resolve("bad-agent.yaml"),
        """
        name: bad-agent
        provider: [broken yaml :::
        """);

    // 3. 合法 Profile 2
    Files.writeString(
        tempDir.resolve("agent2.yaml"),
        """
        name: agent-two
        provider:
          name: qwen
          model: qwen-plus
        """);

    ProfileRegistry registry = new ProfileRegistry();
    ProfileLoader dirLoader = new ProfileLoader(registry);

    List<Profile> loaded = dirLoader.loadProfiles(tempDir);

    // 坏文件被跳过，其余 2 个正常加载并注册
    assertThat(loaded).hasSize(2);
    assertThat(registry.size()).isEqualTo(2);
    assertThat(registry.containsProfile("agent-one")).isTrue();
    assertThat(registry.containsProfile("agent-two")).isTrue();
    assertThat(registry.containsProfile("bad-agent")).isFalse();
  }

  @Test
  @DisplayName("${ENV} 占位符从环境变量或系统属性解析")
  void env占位符从环境变量解析() {
    System.setProperty("TEST_LLM_API_KEY", "sk-resolved-secret-key");
    System.setProperty("TEST_LLM_MODEL", "custom-fast-model");

    try {
      String yaml =
          """
          name: env-agent
          provider:
            name: deepseek
            model: ${TEST_LLM_MODEL:default-model}
            api_key: ${TEST_LLM_API_KEY}
            base_url: ${MISSING_VAR:https://default.api.com}
          """;

      Profile profile = loader.parse(yaml);

      assertThat(profile.getProvider().getModel()).isEqualTo("custom-fast-model");
      assertThat(profile.getProvider().getApiKey()).isEqualTo("sk-resolved-secret-key");
      assertThat(profile.getProvider().getBaseUrl()).isEqualTo("https://default.api.com");
    } finally {
      System.clearProperty("TEST_LLM_API_KEY");
      System.clearProperty("TEST_LLM_MODEL");
    }
  }
}
