package com.oryxos.core.context.impl;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 运行上下文动态加载器实现.
 *
 * <p>每次调用都重新读取磁盘。目录型 Agent 注入 AGENT.md 正文，私有资源由工具按需读取；历史 Profile 保留原有 Skill 行为.
 *
 * @author oryxos
 */
@Component
public class ContextLoaderImpl implements ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoaderImpl.class);
  private static final String FRONTMATTER_DELIMITER = "---";
  private static final int MIN_AGENT_DOCUMENT_LINES = 3;

  private final Path baseWorkspaceDir;

  /** 默认无参构造器，基准路径为当前工作目录. */
  public ContextLoaderImpl() {
    this(Paths.get("."));
  }

  /**
   * 构造函数指定工作空间基准路径.
   *
   * @param baseWorkspaceDir 工作空间基准目录
   */
  public ContextLoaderImpl(Path baseWorkspaceDir) {
    this.baseWorkspaceDir = baseWorkspaceDir != null ? baseWorkspaceDir : Paths.get(".");
  }

  @Override
  public String loadContext(Profile profile) {
    if (profile == null) {
      return "";
    }

    StringBuilder contextBuilder = new StringBuilder();
    boolean directoryAgent = appendAgentInstructions(contextBuilder, profile);
    appendBootstrap(contextBuilder, profile.getBootstrap());
    if (!directoryAgent) {
      appendLegacySkills(contextBuilder, profile);
    }
    return contextBuilder.toString();
  }

  private boolean appendAgentInstructions(StringBuilder contextBuilder, Profile profile) {
    Path agentFile = resolveAgentFile(profile.getName());
    if (agentFile == null || !Files.isRegularFile(agentFile)) {
      return false;
    }
    try {
      String content = Files.readString(agentFile, StandardCharsets.UTF_8);
      String instructions = extractInstructions(content, agentFile);
      appendSection(contextBuilder, "Agent Instructions: " + profile.getName(), instructions);
      return true;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read Agent instructions: " + agentFile, e);
    }
  }

  private void appendBootstrap(StringBuilder contextBuilder, List<String> bootstrapFiles) {
    if (bootstrapFiles == null || bootstrapFiles.isEmpty()) {
      return;
    }
    for (String bootstrapFile : bootstrapFiles) {
      if (bootstrapFile == null || bootstrapFile.isBlank()) {
        continue;
      }
      Path filePath = resolveFilePath(bootstrapFile.trim());
      if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
        try {
          String content = Files.readString(filePath, StandardCharsets.UTF_8);
          appendSection(contextBuilder, "Bootstrap: " + bootstrapFile.trim(), content);
        } catch (IOException e) {
          log.warn("Failed to read bootstrap file: {}", filePath, e);
        }
      } else {
        log.warn("Bootstrap file not found, skipping gracefully: {}", filePath);
      }
    }
  }

  private void appendLegacySkills(StringBuilder contextBuilder, Profile profile) {
    List<String> skillNames = profile.getSkills();
    if (skillNames == null || skillNames.isEmpty()) {
      return;
    }
    for (String skillName : skillNames) {
      if (skillName == null || skillName.isBlank()) {
        continue;
      }
      Path skillPath = resolveSkillPath(profile.getName(), skillName.trim());
      if (!Files.exists(skillPath) || !Files.isRegularFile(skillPath)) {
        throw new IllegalStateException(
            "Required skill file not found for skill: " + skillName + " at " + skillPath);
      }

      try {
        String content = Files.readString(skillPath, StandardCharsets.UTF_8);
        appendSection(contextBuilder, "Skill: " + skillName.trim(), content);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read skill file: " + skillPath, e);
      }
    }
  }

  private void appendSection(StringBuilder contextBuilder, String title, String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    if (contextBuilder.length() > 0) {
      contextBuilder.append("\n\n");
    }
    contextBuilder.append("=== ").append(title).append(" ===\n").append(content.trim());
  }

  private String extractInstructions(String content, Path agentFile) {
    String normalized = content != null ? content.replace("\r\n", "\n") : "";
    String[] lines = normalized.split("\n", -1);
    if (lines.length < MIN_AGENT_DOCUMENT_LINES || !FRONTMATTER_DELIMITER.equals(lines[0].trim())) {
      throw new IllegalStateException("Invalid AGENT.md frontmatter: " + agentFile);
    }
    int closingLine = -1;
    for (int index = 1; index < lines.length; index++) {
      if (FRONTMATTER_DELIMITER.equals(lines[index].trim())) {
        closingLine = index;
        break;
      }
    }
    if (closingLine < 0) {
      throw new IllegalStateException("Invalid AGENT.md frontmatter: " + agentFile);
    }
    String instructions =
        String.join("\n", java.util.Arrays.copyOfRange(lines, closingLine + 1, lines.length))
            .trim();
    if (instructions.isEmpty()) {
      throw new IllegalStateException("Agent instructions body is required: " + agentFile);
    }
    return instructions;
  }

  private Path resolveAgentFile(String agentName) {
    if (agentName == null || agentName.isBlank()) {
      return null;
    }
    Path agentsRoot =
        baseWorkspaceDir.toAbsolutePath().normalize().resolve(".oryxos").resolve("agents");
    Path candidate = agentsRoot.resolve(agentName.trim()).resolve("AGENT.md").normalize();
    return candidate.startsWith(agentsRoot) ? candidate : null;
  }

  private Path resolveFilePath(String relativePath) {
    Path candidate = baseWorkspaceDir.resolve(relativePath);
    if (Files.exists(candidate)) {
      return candidate;
    }
    Path dotOryxosCandidate = baseWorkspaceDir.resolve(".oryxos").resolve(relativePath);
    if (Files.exists(dotOryxosCandidate)) {
      return dotOryxosCandidate;
    }
    return candidate;
  }

  private Path resolveSkillPath(String agentName, String skillName) {
    if (agentName != null) {
      Path agentSkill =
          baseWorkspaceDir
              .resolve(".oryxos")
              .resolve("agents")
              .resolve(agentName)
              .resolve("skills")
              .resolve(skillName)
              .resolve("SKILL.md");
      if (Files.exists(agentSkill)) {
        return agentSkill;
      }
    }

    Path globalSkill =
        baseWorkspaceDir
            .resolve(".oryxos")
            .resolve("skills")
            .resolve(skillName)
            .resolve("SKILL.md");
    if (Files.exists(globalSkill)) {
      return globalSkill;
    }

    Path directSkill = baseWorkspaceDir.resolve("skills").resolve(skillName).resolve("SKILL.md");
    if (Files.exists(directSkill)) {
      return directSkill;
    }

    return globalSkill;
  }
}
