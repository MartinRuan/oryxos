package com.oryxos.core.profile;

import com.oryxos.core.OryxTool;
import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.provider.ProviderService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent 目录加载器.
 *
 * <p>一个目录定义一个 Agent：AGENT.md 的 frontmatter 派生运行时 Profile，正文与私有资源保留在目录内按需加载.
 *
 * @author oryxos
 */
@Component
public class AgentLoader {

  private static final Logger log = LoggerFactory.getLogger(AgentLoader.class);
  private static final String AGENT_FILE = "AGENT.md";
  private static final String REFERENCE_FILE = "REFERENCE.md";
  private static final String FRONTMATTER_DELIMITER = "---";
  private static final int MIN_AGENT_DOCUMENT_LINES = 3;

  private final ProfileLoader profileLoader;
  private final ProfileRegistry profileRegistry;
  private final AgentScheduler agentScheduler;
  private final Set<String> availableProviders;
  private final Set<String> availableTools;

  /**
   * 创建运行时目录加载器.
   *
   * @param profileLoader 既有 Profile YAML 映射器
   * @param profileRegistry Profile 注册中心
   * @param agentScheduler Agent 调度器
   * @param providerService Provider 注册门面
   * @param tools 已注册底座工具
   */
  @Autowired
  public AgentLoader(
      ProfileLoader profileLoader,
      ProfileRegistry profileRegistry,
      AgentScheduler agentScheduler,
      ProviderService providerService,
      List<OryxTool> tools) {
    this(
        profileLoader,
        profileRegistry,
        agentScheduler,
        providerService.listProviders().stream()
            .map(descriptor -> descriptor.getName())
            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        tools.stream()
            .map(OryxTool::getName)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
  }

  AgentLoader(
      ProfileLoader profileLoader,
      ProfileRegistry profileRegistry,
      AgentScheduler agentScheduler,
      Set<String> availableProviders,
      Set<String> availableTools) {
    this.profileLoader = java.util.Objects.requireNonNull(profileLoader, "profileLoader");
    this.profileRegistry = java.util.Objects.requireNonNull(profileRegistry, "profileRegistry");
    this.agentScheduler = java.util.Objects.requireNonNull(agentScheduler, "agentScheduler");
    this.availableProviders =
        availableProviders != null ? Set.copyOf(availableProviders) : Collections.emptySet();
    this.availableTools =
        availableTools != null ? Set.copyOf(availableTools) : Collections.emptySet();
  }

  /**
   * 从 Agent 目录派生既有运行时 Profile.
   *
   * @param agentDir Agent 目录
   * @return 完整 Profile
   */
  public Profile deriveProfile(Path agentDir) {
    AgentDefinition definition = load(agentDir);
    Profile profile = profileLoader.parse(definition.frontmatter(), availableProviders);
    String directoryName = fileName(definition.directory());
    if (!directoryName.equals(profile.getName())) {
      throw invalid(
          "Agent name ["
              + profile.getName()
              + "] must match directory name ["
              + directoryName
              + "]");
    }
    warnUnknownTools(profile);
    return profile;
  }

  /**
   * 扫描直接子目录并注册合法 Agent.
   *
   * @param agentsRoot Agent 根目录
   * @return 注册成功的 Profile
   */
  public List<Profile> scanAndRegister(Path agentsRoot) {
    if (agentsRoot == null || !Files.isDirectory(agentsRoot)) {
      log.debug("Agent directory not found: {}", agentsRoot);
      return List.of();
    }
    List<Path> directories;
    try (Stream<Path> stream = Files.list(agentsRoot)) {
      directories =
          stream.filter(Files::isDirectory).sorted(Comparator.comparing(this::fileName)).toList();
    } catch (IOException e) {
      throw new OryxException(
          StandardErrorCode.INTERNAL_ERROR, "Failed to scan Agent directory: " + agentsRoot, e);
    }

    java.util.ArrayList<Profile> loaded = new java.util.ArrayList<>();
    for (Path directory : directories) {
      try {
        Profile profile = deriveProfile(directory);
        profileRegistry.register(profile);
        try {
          if (!profile.getSchedules().isEmpty()) {
            agentScheduler.registerProfile(profile);
          }
        } catch (RuntimeException e) {
          profileRegistry.remove(profile.getName());
          throw e;
        }
        loaded.add(profile);
        log.info("Registered Agent [{}] from {}", profile.getName(), directory);
      } catch (RuntimeException e) {
        log.error("Failed to load Agent from {}: {}", directory, e.getMessage(), e);
      }
    }
    return List.copyOf(loaded);
  }

  AgentDefinition load(Path agentDir) {
    if (agentDir == null || !Files.isDirectory(agentDir)) {
      throw invalid("Agent directory does not exist: " + agentDir);
    }
    Path normalizedDirectory = agentDir.toAbsolutePath().normalize();
    Path agentFile = normalizedDirectory.resolve(AGENT_FILE);
    if (!Files.isRegularFile(agentFile)) {
      throw invalid("Agent main file not found: " + agentFile);
    }

    String content;
    try {
      content = Files.readString(agentFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new OryxException(
          StandardErrorCode.INTERNAL_ERROR, "Failed to read Agent main file: " + agentFile, e);
    }

    ParsedDocument document = splitDocument(content, agentFile);
    List<Path> skillFiles = listFiles(normalizedDirectory.resolve("skills"), ".md");
    List<Path> scriptFiles = listFiles(normalizedDirectory.resolve("scripts"), null);
    Path reference = normalizedDirectory.resolve(REFERENCE_FILE);
    Optional<Path> referenceFile =
        Files.isRegularFile(reference) ? Optional.of(reference) : Optional.empty();
    return new AgentDefinition(
        normalizedDirectory,
        document.frontmatter(),
        document.instructions(),
        skillFiles,
        scriptFiles,
        referenceFile);
  }

  private ParsedDocument splitDocument(String content, Path agentFile) {
    String normalized = content != null ? content.replace("\r\n", "\n") : "";
    String[] lines = normalized.split("\n", -1);
    if (lines.length < MIN_AGENT_DOCUMENT_LINES || !FRONTMATTER_DELIMITER.equals(lines[0].trim())) {
      throw invalid("Invalid AGENT.md frontmatter in " + agentFile);
    }
    int closingLine = -1;
    for (int index = 1; index < lines.length; index++) {
      if (FRONTMATTER_DELIMITER.equals(lines[index].trim())) {
        closingLine = index;
        break;
      }
    }
    if (closingLine < 0) {
      throw invalid("Invalid AGENT.md frontmatter in " + agentFile + ": closing delimiter missing");
    }
    String frontmatter =
        String.join("\n", java.util.Arrays.copyOfRange(lines, 1, closingLine)).trim();
    String instructions =
        String.join("\n", java.util.Arrays.copyOfRange(lines, closingLine + 1, lines.length))
            .trim();
    if (frontmatter.isEmpty()) {
      throw invalid("Invalid AGENT.md frontmatter in " + agentFile + ": content is empty");
    }
    if (instructions.isEmpty()) {
      throw invalid("Agent instructions body is required in " + agentFile);
    }
    return new ParsedDocument(frontmatter, instructions);
  }

  private List<Path> listFiles(Path directory, String suffix) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> stream = Files.list(directory)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> suffix == null || fileName(path).endsWith(suffix))
          .sorted(Comparator.comparing(this::fileName))
          .toList();
    } catch (IOException e) {
      throw new OryxException(
          StandardErrorCode.INTERNAL_ERROR, "Failed to list Agent resources: " + directory, e);
    }
  }

  private String fileName(Path path) {
    Path fileName = path.getFileName();
    if (fileName == null) {
      throw invalid("Agent path must have a file name: " + path);
    }
    return fileName.toString();
  }

  private void warnUnknownTools(Profile profile) {
    for (String toolName : profile.getTools()) {
      if (!availableTools.contains(toolName)) {
        log.warn("Agent [{}] references unregistered tool [{}]", profile.getName(), toolName);
      }
    }
  }

  private OryxException invalid(String message) {
    return new OryxException(StandardErrorCode.INVALID_PARAMETER, message);
  }

  record AgentDefinition(
      Path directory,
      String frontmatter,
      String instructions,
      List<Path> skillFiles,
      List<Path> scriptFiles,
      Optional<Path> referenceFile) {}

  private record ParsedDocument(String frontmatter, String instructions) {}
}
