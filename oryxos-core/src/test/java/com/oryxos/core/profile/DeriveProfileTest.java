package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oryxos.core.model.Profile;
import com.oryxos.core.scheduler.AgentScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeriveProfileTest {

  @Test
  void frontmatter全部字段与定时原样派生(@TempDir Path tempDir) throws Exception {
    Path agentDir = tempDir.resolve("daily-reconcile");
    Files.createDirectories(agentDir);
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        """
        ---
        name: daily-reconcile
        description: 每日对账
        identity:
          agent_name: 对账小欧
          prompt: 只依据确定性数据下结论
        provider:
          name: deepseek
          model: deepseek-chat
          temperature: 0.2
        tools: [shell, read_file]
        notify_channels:
          - {name: ops, type: webhook, url: https://example.test/hook}
        bootstrap: [AGENTS.md]
        schedules:
          - {id: morning, cron: "0 0 9 * * *", zone: Asia/Shanghai, message: 开始对账}
        settings:
          max_iterations: 6
          max_history_turns: 12
        ---
        严格按顺序执行每日对账。
        """);

    ProfileRegistry registry = new ProfileRegistry();
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(registry),
            registry,
            mock(AgentScheduler.class),
            Set.of("deepseek"),
            Set.of("shell", "read_file"));

    Profile profile = loader.deriveProfile(agentDir);

    assertThat(profile.getName()).isEqualTo("daily-reconcile");
    assertThat(profile.getDescription()).isEqualTo("每日对账");
    assertThat(profile.getIdentity().getAgentName()).isEqualTo("对账小欧");
    assertThat(profile.getIdentity().getPrompt()).isEqualTo("只依据确定性数据下结论");
    assertThat(profile.getProvider().getName()).isEqualTo("deepseek");
    assertThat(profile.getProvider().getModel()).isEqualTo("deepseek-chat");
    assertThat(profile.getProvider().getTemperature()).isEqualTo(0.2);
    assertThat(profile.getTools()).containsExactly("shell", "read_file");
    assertThat(profile.getNotifyChannels())
        .singleElement()
        .satisfies(channel -> assertThat(channel.getUrl()).isEqualTo("https://example.test/hook"));
    assertThat(profile.getBootstrap()).containsExactly("AGENTS.md");
    assertThat(profile.getSettings().getMaxIterations()).isEqualTo(6);
    assertThat(profile.getSettings().getMaxHistoryTurns()).isEqualTo(12);
    assertThat(profile.getSchedules())
        .singleElement()
        .satisfies(
            schedule -> {
              assertThat(schedule.getId()).isEqualTo("morning");
              assertThat(schedule.getCron()).isEqualTo("0 0 9 * * *");
              assertThat(schedule.getTimezone()).isEqualTo("Asia/Shanghai");
              assertThat(schedule.getMessage()).isEqualTo("开始对账");
            });
  }

  @Test
  void name与目录名不一致时拒绝派生(@TempDir Path tempDir) throws Exception {
    Path agentDir = tempDir.resolve("directory-name");
    Files.createDirectories(agentDir);
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        "---\nname: another-name\nprovider:\n  name: deepseek\n---\n任务正文");

    ProfileRegistry registry = new ProfileRegistry();
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(registry),
            registry,
            mock(AgentScheduler.class),
            Set.of("deepseek"),
            Set.of());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> loader.deriveProfile(agentDir))
        .isInstanceOf(com.oryxos.core.exception.OryxException.class)
        .hasMessageContaining("directory-name")
        .hasMessageContaining("another-name");
  }
}
