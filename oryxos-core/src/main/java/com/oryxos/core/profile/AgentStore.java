package com.oryxos.core.profile;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Agent 工作区文件存储，集中实现名称、路径、原子写入与归档约束.
 *
 * @author oryxos
 */
public final class AgentStore {

  private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
  private static final String AGENT_FILE = "AGENT.md";
  private final Path workspaceRoot;
  private final Path agentsRoot;
  private final Path archiveRoot;
  private final Clock clock;

  public AgentStore(Path workspaceRoot) {
    this(workspaceRoot, Clock.systemUTC());
  }

  AgentStore(Path workspaceRoot, Clock clock) {
    this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    this.agentsRoot = this.workspaceRoot.resolve("agents");
    this.archiveRoot = this.workspaceRoot.resolve("archive");
    this.clock = clock;
    createDirectories(this.agentsRoot);
    createDirectories(this.archiveRoot);
  }

  public Path agentsRoot() {
    return agentsRoot;
  }

  public Path agentDirectory(String name) {
    validateName(name);
    return agentsRoot.resolve(name).normalize();
  }

  public boolean exists(String name) {
    return Files.isDirectory(agentDirectory(name), LinkOption.NOFOLLOW_LINKS);
  }

  /** 创建 Agent 目录并原子写入主文件. */
  public void create(String name, String agentMarkdown) {
    Path directory = agentDirectory(name);
    if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.AGENT_ALREADY_EXISTS, "Agent already exists: " + name);
    }
    try {
      Files.createDirectory(directory);
      writeAtomic(directory.resolve(AGENT_FILE), agentMarkdown);
    } catch (IOException | RuntimeException e) {
      deleteRecursively(directory);
      if (e instanceof OryxException oryxException) {
        throw oryxException;
      }
      throw internal("Failed to create Agent: " + name, e);
    }
  }

  /** 读取指定 Agent 的完整定义. */
  public String read(String name) {
    Path file = agentDirectory(name).resolve(AGENT_FILE);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.PROFILE_NOT_FOUND, "Agent not found: " + name);
    }
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw internal("Failed to read Agent: " + name, e);
    }
  }

  /** 原子更新 Agent 定义并返回旧内容. */
  public String update(String name, String agentMarkdown) {
    String previous = read(name);
    writeAtomic(agentDirectory(name).resolve(AGENT_FILE), agentMarkdown);
    return previous;
  }

  /** 恢复 Agent 定义内容. */
  public void restore(String name, String agentMarkdown) {
    writeAtomic(agentDirectory(name).resolve(AGENT_FILE), agentMarkdown);
  }

  /** 删除创建失败留下的完整 Agent 目录. */
  public void deleteCreated(String name) {
    deleteRecursively(agentDirectory(name));
  }

  /** 将完整 Agent 目录移动到唯一的归档路径. */
  public Path archive(String name) {
    Path source = agentDirectory(name);
    if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.PROFILE_NOT_FOUND, "Agent not found: " + name);
    }
    String timestamp =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            .withZone(java.time.ZoneOffset.UTC)
            .format(clock.instant());
    Path target = archiveRoot.resolve(name + "-" + timestamp);
    while (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      target = archiveRoot.resolve(name + "-" + timestamp + "-" + UUID.randomUUID());
    }
    try {
      return Files.move(source, target);
    } catch (IOException e) {
      throw internal("Failed to archive Agent: " + name, e);
    }
  }

  /** 读取 agents/archive 范围内普通 UTF-8 文件，拒绝目录、绝对路径和软链接越界. */
  public String readWorkspaceFile(String relativePath) {
    Path file = resolveWorkspacePath(relativePath, true);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.INVALID_PARAMETER, "Workspace path is not a regular file");
    }
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw internal("Failed to read workspace file", e);
    }
  }

  /** 列出工作区目录的直接子项，结果按目录优先、名称排序. */
  public List<WorkspaceEntry> listDirectory(String relativePath) {
    Path directory = resolveWorkspacePath(relativePath, false);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.NOT_FOUND, "Workspace directory not found: " + relativePath);
    }
    try (Stream<Path> stream = Files.list(directory)) {
      return stream
          .filter(path -> !Files.isSymbolicLink(path))
          .sorted(
              Comparator.comparing((Path p) -> !Files.isDirectory(p)).thenComparing(this::fileName))
          .map(
              path ->
                  new WorkspaceEntry(
                      fileName(path),
                      workspaceRoot.relativize(path).toString().replace('\\', '/'),
                      Files.isDirectory(path),
                      Files.isReadable(path)))
          .toList();
    } catch (IOException e) {
      throw internal("Failed to list workspace directory", e);
    }
  }

  /** 工作区直接子项. */
  public record WorkspaceEntry(String name, String path, boolean directory, boolean readable) {}

  private Path resolveWorkspacePath(String relativePath, boolean requireExisting) {
    if (relativePath == null || relativePath.isBlank()) {
      throw error(StandardErrorCode.INVALID_PARAMETER, "Workspace path is required");
    }
    Path supplied = Path.of(relativePath);
    if (supplied.isAbsolute()) {
      throw error(StandardErrorCode.INVALID_PARAMETER, "Absolute workspace path is forbidden");
    }
    Path resolved = workspaceRoot.resolve(supplied).normalize();
    boolean insideWorkspace = resolved.startsWith(workspaceRoot);
    boolean insideManagedArea = resolved.startsWith(agentsRoot) || resolved.startsWith(archiveRoot);
    if (!insideWorkspace || !insideManagedArea) {
      throw error(StandardErrorCode.INVALID_PARAMETER, "Workspace path is outside agents/archive");
    }
    if (requireExisting && !Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
      throw error(StandardErrorCode.NOT_FOUND, "Workspace file not found: " + relativePath);
    }
    try {
      Path realRoot = workspaceRoot.toRealPath();
      Path real = resolved.toRealPath();
      if (!real.startsWith(realRoot)) {
        throw error(StandardErrorCode.INVALID_PARAMETER, "Workspace symlink escapes root");
      }
    } catch (IOException e) {
      if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
        throw error(StandardErrorCode.INVALID_PARAMETER, "Workspace path cannot be resolved");
      }
    }
    return resolved;
  }

  private void validateName(String name) {
    if (name == null || !SAFE_NAME.matcher(name).matches()) {
      throw error(StandardErrorCode.INVALID_PARAMETER, "Invalid Agent name: " + name);
    }
  }

  private void writeAtomic(Path target, String content) {
    if (content == null || content.isBlank()) {
      throw error(StandardErrorCode.AGENT_DEFINITION_INVALID, "AGENT.md must not be blank");
    }
    Path temporary = target.resolveSibling("." + AGENT_FILE + "." + UUID.randomUUID() + ".tmp");
    try {
      Files.writeString(temporary, content, StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException cleanupError) {
        e.addSuppressed(cleanupError);
      }
      throw internal("Failed to write " + target, e);
    }
  }

  private void createDirectories(Path directory) {
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw internal("Failed to create workspace directory: " + directory, e);
    }
  }

  private void deleteRecursively(Path root) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(root)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  throw new DeleteFailureException(e);
                }
              });
    } catch (IOException | DeleteFailureException e) {
      Throwable cause = e instanceof DeleteFailureException ? e.getCause() : e;
      throw internal("Failed to delete Agent directory: " + root, cause);
    }
  }

  private String fileName(Path path) {
    return java.util.Objects.requireNonNull(path.getFileName(), "path file name").toString();
  }

  private OryxException error(StandardErrorCode code, String message) {
    return new OryxException(code, message);
  }

  private OryxException internal(String message, Throwable cause) {
    return new OryxException(StandardErrorCode.INTERNAL_ERROR, message, cause);
  }

  private static final class DeleteFailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    DeleteFailureException(Throwable cause) {
      super(cause);
    }
  }
}
