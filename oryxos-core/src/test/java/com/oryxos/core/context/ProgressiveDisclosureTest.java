package com.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.context.impl.ContextLoaderImpl;
import com.oryxos.core.model.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressiveDisclosureTest {

  @Test
  void Agent正文进入prompt_私有资源不预载_修改后即时生效(@TempDir Path tempDir) throws Exception {
    Path agentDir = tempDir.resolve(".oryxos/agents/report-agent");
    Files.createDirectories(agentDir.resolve("skills"));
    Files.createDirectories(agentDir.resolve("scripts"));
    Path agentFile = agentDir.resolve("AGENT.md");
    Files.writeString(
        agentFile,
        """
        ---
        name: report-agent
        provider:
          name: deepseek
        tools: [read_file, shell]
        ---
        MAIN_INSTRUCTION_V1：先判断是否需要读取资源。
        """);
    Files.writeString(agentDir.resolve("skills/report-format.md"), "PRIVATE_SKILL_MARKER");
    Files.writeString(agentDir.resolve("REFERENCE.md"), "PRIVATE_REFERENCE_MARKER");
    Files.writeString(agentDir.resolve("scripts/report.py"), "PRIVATE_SCRIPT_MARKER");

    Profile profile =
        Profile.builder()
            .name("report-agent")
            .provider(new Profile.ProviderConfig("deepseek", "model", 0.2))
            .skills(List.of("report-format"))
            .build();
    ContextLoader loader = new ContextLoaderImpl(tempDir);

    String first = loader.loadContext(profile);

    assertThat(first).contains("MAIN_INSTRUCTION_V1");
    assertThat(first).doesNotContain("name: report-agent");
    assertThat(first).doesNotContain("PRIVATE_SKILL_MARKER");
    assertThat(first).doesNotContain("PRIVATE_REFERENCE_MARKER");
    assertThat(first).doesNotContain("PRIVATE_SCRIPT_MARKER");

    Files.writeString(
        agentFile,
        """
        ---
        name: report-agent
        provider:
          name: deepseek
        ---
        MAIN_INSTRUCTION_V2：正文已经更新。
        """);

    String second = loader.loadContext(profile);
    assertThat(second).contains("MAIN_INSTRUCTION_V2").doesNotContain("MAIN_INSTRUCTION_V1");
  }
}
