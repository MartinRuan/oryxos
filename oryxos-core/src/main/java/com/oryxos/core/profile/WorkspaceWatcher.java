package com.oryxos.core.profile;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监听 Agent 工作区目录并将文件变化同步到统一生命周期服务.
 *
 * @author oryxos
 */
public final class WorkspaceWatcher implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceWatcher.class);
  private static final String AGENT_FILE = "AGENT.md";
  private final Path agentsRoot;
  private final AgentLifecycleService lifecycleService;
  private final WatchService watchService;
  private final Map<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();
  private final AtomicBoolean running = new AtomicBoolean();
  private Thread worker;

  /** 创建指定 Agent 根目录的监听器. */
  public WorkspaceWatcher(Path agentsRoot, AgentLifecycleService lifecycleService) {
    this.agentsRoot = agentsRoot.toAbsolutePath().normalize();
    this.lifecycleService = lifecycleService;
    try {
      Files.createDirectories(this.agentsRoot);
      this.watchService = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to initialize workspace watcher", e);
    }
  }

  /** 同步初扫后启动唯一守护监听线程. */
  @SuppressWarnings("PMD.AvoidManuallyCreateThreadRule")
  public synchronized void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    registerDirectory(agentsRoot);
    scanExisting();
    worker = new Thread(this::watchLoop, "oryx-workspace-watcher");
    worker.setDaemon(true);
    worker.start();
  }

  private void scanExisting() {
    try (java.util.stream.Stream<Path> stream = Files.list(agentsRoot)) {
      stream.filter(Files::isDirectory).sorted().forEach(this::registerAgentDirectory);
    } catch (IOException e) {
      log.error("Failed to scan Agent workspace: {}", agentsRoot, e);
    }
  }

  private void registerAgentDirectory(Path directory) {
    registerDirectory(directory);
    if (Files.isRegularFile(directory.resolve(AGENT_FILE))) {
      safeRegister(directory);
    }
  }

  private void registerDirectory(Path directory) {
    try {
      WatchKey key =
          directory.register(
              watchService,
              StandardWatchEventKinds.ENTRY_CREATE,
              StandardWatchEventKinds.ENTRY_MODIFY,
              StandardWatchEventKinds.ENTRY_DELETE);
      watchedDirectories.put(key, directory);
    } catch (IOException e) {
      log.error("Failed to watch directory: {}", directory, e);
    }
  }

  private void watchLoop() {
    while (running.get()) {
      try {
        WatchKey key = watchService.take();
        Path watched = watchedDirectories.get(key);
        if (watched != null) {
          for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
              continue;
            }
            Path changed = watched.resolve((Path) event.context());
            handleEvent(watched, changed, event.kind());
          }
        }
        if (!key.reset()) {
          watchedDirectories.remove(key);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (ClosedWatchServiceException e) {
        break;
      } catch (RuntimeException e) {
        log.error("Workspace watcher event failed", e);
      }
    }
  }

  private void handleEvent(Path watched, Path changed, WatchEvent.Kind<?> kind) {
    if (watched.equals(agentsRoot)) {
      String name = fileName(changed);
      if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
        safeUnregister(name);
      } else if (Files.isDirectory(changed)) {
        registerAgentDirectory(changed);
      }
      return;
    }
    if (AGENT_FILE.equals(fileName(changed))) {
      if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
        safeUnregister(fileName(watched));
      } else {
        safeRegister(watched);
      }
    }
  }

  private void safeRegister(Path directory) {
    try {
      lifecycleService.register(directory);
      log.info("Agent workspace synchronized: {}", fileName(directory));
    } catch (RuntimeException e) {
      log.error("Failed to synchronize Agent directory {}: {}", directory, e.getMessage());
    }
  }

  private String fileName(Path path) {
    return java.util.Objects.requireNonNull(path.getFileName(), "path file name").toString();
  }

  private void safeUnregister(String name) {
    try {
      lifecycleService.unregister(name);
      log.info("Agent workspace removed from runtime: {}", name);
    } catch (RuntimeException e) {
      log.error("Failed to unregister Agent {}: {}", name, e.getMessage());
    }
  }

  @Override
  public synchronized void close() {
    running.set(false);
    try {
      watchService.close();
    } catch (IOException e) {
      log.warn("Failed to close workspace watcher", e);
    }
    if (worker != null) {
      worker.interrupt();
    }
  }
}
