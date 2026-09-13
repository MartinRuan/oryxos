package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.model.Profile;
import com.oryxos.core.scheduler.AgentScheduler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoaderTest {

  @TempDir Path tempDir;

  @Test
  void 正确拆出frontmatter与正文_并识别私有资源() throws IOException {
    Path agentDir = tempDir.resolve("daily-reconcile");
    Files.createDirectories(agentDir.resolve("skills"));
    Files.createDirectories(agentDir.resolve("scripts"));
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        """
        ---
        name: daily-reconcile
        provider:
          name: deepseek
        ---
        这是 Agent 主任务正文。
        """);
    Files.writeString(agentDir.resolve("skills/report-format.md"), "报告规范");
    Files.writeString(agentDir.resolve("scripts/reconcile.py"), "print('{}')");
    Files.writeString(agentDir.resolve("REFERENCE.md"), "参考资料");

    AgentLoader.AgentDefinition definition = loader().load(agentDir);

    assertThat(definition.frontmatter()).contains("name: daily-reconcile");
    assertThat(definition.instructions()).isEqualTo("这是 Agent 主任务正文。");
    assertThat(definition.skillFiles())
        .extracting(Path::getFileName)
        .extracting(Path::toString)
        .containsExactly("report-format.md");
    assertThat(definition.scriptFiles())
        .extracting(Path::getFileName)
        .extracting(Path::toString)
        .containsExactly("reconcile.py");
    assertThat(definition.referenceFile()).contains(agentDir.resolve("REFERENCE.md"));
  }

  @Test
  void 缺name或provider时_报错必须点名字段() throws IOException {
    Path missingName = writeAgent("missing-name", "provider:\n  name: deepseek");
    Path missingProvider = writeAgent("missing-provider", "name: missing-provider");

    assertThatThrownBy(() -> loader().deriveProfile(missingName))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("name");
    assertThatThrownBy(() -> loader().deriveProfile(missingProvider))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("provider");
  }

  @Test
  void frontmatter不完整或正文为空时_拒绝加载() throws IOException {
    Path broken = tempDir.resolve("broken");
    Files.createDirectories(broken);
    Files.writeString(broken.resolve("AGENT.md"), "---\nname: broken\nprovider:\n  name: deepseek");

    Path noBody = tempDir.resolve("no-body");
    Files.createDirectories(noBody);
    Files.writeString(
        noBody.resolve("AGENT.md"), "---\nname: no-body\nprovider:\n  name: deepseek\n---\n");

    assertThatThrownBy(() -> loader().deriveProfile(broken))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("frontmatter");
    assertThatThrownBy(() -> loader().deriveProfile(noBody))
        .isInstanceOf(OryxException.class)
        .hasMessageContaining("instructions");
  }

  @Test
  void 仓库dailyReconcile示例必须能被真实解析器加载() {
    Path projectRoot = Path.of("").toAbsolutePath().normalize();
    if (!Files.isDirectory(projectRoot.resolve(".oryxos"))) {
      projectRoot = projectRoot.getParent();
    }
    Path agentDir = projectRoot.resolve(".oryxos/agents/daily-reconcile");

    Profile profile = loader().deriveProfile(agentDir);

    assertThat(profile.getName()).isEqualTo("daily-reconcile");
    assertThat(profile.getProvider().getName()).isEqualTo("minimax");
    assertThat(profile.getProvider().getModel()).isEqualTo("MiniMax-M2.7");
    assertThat(profile.getProvider().getBaseUrl()).isEqualTo("https://api.minimaxi.com/v1");
    assertThat(profile.getSchedules())
        .singleElement()
        .satisfies(
            schedule -> {
              assertThat(schedule.getId()).isEqualTo("reconcile-morning");
              assertThat(schedule.getCron()).isEqualTo("0 0 9 * * *");
              assertThat(schedule.getZoneId().toString()).isEqualTo("Asia/Shanghai");
            });
  }

  private Path writeAgent(String directory, String frontmatter) throws IOException {
    Path agentDir = tempDir.resolve(directory);
    Files.createDirectories(agentDir);
    Files.writeString(agentDir.resolve("AGENT.md"), "---\n" + frontmatter + "\n---\n任务正文");
    return agentDir;
  }

  private AgentLoader loader() {
    ProfileRegistry registry = new ProfileRegistry();
    return new AgentLoader(
        new ProfileLoader(registry),
        registry,
        mock(AgentScheduler.class),
        Set.of("deepseek", "minimax"),
        Set.of("read_file", "shell", "notify", "save_memory"));
  }
}
