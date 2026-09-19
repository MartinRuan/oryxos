package com.oryxos.core.profile;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceWatcherTest {

  @Test
  void 初扫新增修改删除均同步且可关闭(@TempDir Path root) throws Exception {
    Path agents = root.resolve("agents");
    Path first = agents.resolve("first");
    Files.createDirectories(first);
    Files.writeString(first.resolve("AGENT.md"), "first");
    AgentLifecycleService lifecycle = mock(AgentLifecycleService.class);

    try (WorkspaceWatcher watcher = new WorkspaceWatcher(agents, lifecycle)) {
      watcher.start();
      verify(lifecycle).register(first);

      Path second = agents.resolve("second");
      Files.createDirectory(second);
      Files.writeString(second.resolve("AGENT.md"), "second");
      verify(lifecycle, timeout(3000).atLeastOnce()).register(second);

      Files.writeString(second.resolve("AGENT.md"), "updated");
      verify(lifecycle, timeout(3000).atLeast(2)).register(second);

      Files.delete(second.resolve("AGENT.md"));
      Files.delete(second);
      verify(lifecycle, timeout(3000).atLeastOnce()).unregister("second");
    }
  }

  @Test
  void 单个坏目录不拖垮后续监听(@TempDir Path root) throws Exception {
    Path agents = root.resolve("agents");
    Files.createDirectories(agents);
    AgentLifecycleService lifecycle = mock(AgentLifecycleService.class);
    Path bad = agents.resolve("bad");
    doThrow(new IllegalArgumentException("partial definition")).when(lifecycle).register(bad);

    try (WorkspaceWatcher watcher = new WorkspaceWatcher(agents, lifecycle)) {
      watcher.start();
      Files.createDirectory(bad);
      Files.writeString(bad.resolve("AGENT.md"), "partial");
      verify(lifecycle, timeout(3000).atLeastOnce()).register(bad);

      Path good = agents.resolve("good");
      Files.createDirectory(good);
      Files.writeString(good.resolve("AGENT.md"), "complete");
      verify(lifecycle, timeout(3000).atLeastOnce()).register(good);
    }
  }
}
