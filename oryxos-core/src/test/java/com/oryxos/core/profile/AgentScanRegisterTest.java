package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oryxos.core.model.Profile;
import com.oryxos.core.scheduler.AgentScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class AgentScanRegisterTest {

  @Test
  void 扫描N个Agent_合法项注册并调度_坏目录无部分状态(@TempDir Path tempDir) throws Exception {
    writeAgent(tempDir, "alpha", "[read_file]", true);
    writeAgent(tempDir, "beta", "[missing_tool]", true);
    writeAgent(tempDir, "broken", "[]", false);
    Files.writeString(tempDir.resolve("README.md"), "普通文件不是 Agent");

    ProfileRegistry registry = new ProfileRegistry();
    AgentScheduler scheduler = mock(AgentScheduler.class);
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(registry),
            registry,
            scheduler,
            Set.of("deepseek"),
            Set.of("read_file"));

    Logger logger = (Logger) LoggerFactory.getLogger(AgentLoader.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      List<Profile> loaded = loader.scanAndRegister(tempDir);

      assertThat(loaded).extracting(Profile::getName).containsExactly("alpha", "beta");
      assertThat(registry.size()).isEqualTo(2);
      assertThat(registry.containsProfile("broken")).isFalse();
      verify(scheduler, times(2)).registerProfile(org.mockito.ArgumentMatchers.any(Profile.class));
      assertThat(appender.list)
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anySatisfy(
              message -> {
                assertThat(message).contains("beta");
                assertThat(message).contains("missing_tool");
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private void writeAgent(Path root, String name, String tools, boolean includeProvider)
      throws Exception {
    Path agentDir = root.resolve(name);
    Files.createDirectories(agentDir);
    String provider = includeProvider ? "provider:\n  name: deepseek\n" : "";
    Files.writeString(
        agentDir.resolve("AGENT.md"),
        "---\nname: "
            + name
            + "\n"
            + provider
            + "tools: "
            + tools
            + "\nschedules:\n"
            + "  - {id: daily, cron: \"0 0 9 * * *\", zone: Asia/Shanghai, message: run}\n"
            + "---\n任务正文");
  }
}
