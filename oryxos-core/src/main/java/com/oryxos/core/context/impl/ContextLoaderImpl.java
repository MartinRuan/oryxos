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
 * <p>核心特征： 1. 无内存缓存：每次调用实时读取文件系统，外部文件修改后下一轮即时生效 2. 软容错：Bootstrap 文件缺失时记录 WARN 日志并优雅跳过 3. 硬校验：显式指定的
 * Skill 文件缺失时抛出 FileNotFoundException 异常
 *
 * @author oryxos
 */
@Component
public class ContextLoaderImpl implements ContextLoader {

  private static final Logger log = LoggerFactory.getLogger(ContextLoaderImpl.class);

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

    // 1. 加载 Bootstrap 文件 (AGENTS.md, SOUL.md, USER.md 等)
    List<String> bootstrapFiles = profile.getBootstrap();
    if (bootstrapFiles != null && !bootstrapFiles.isEmpty()) {
      for (String bootstrapFile : bootstrapFiles) {
        if (bootstrapFile == null || bootstrapFile.isBlank()) {
          continue;
        }
        Path filePath = resolveFilePath(bootstrapFile.trim());
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
          try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (!content.isBlank()) {
              if (contextBuilder.length() > 0) {
                contextBuilder.append("\n\n");
              }
              contextBuilder
                  .append("=== Bootstrap: ")
                  .append(bootstrapFile.trim())
                  .append(" ===\n")
                  .append(content.trim());
            }
          } catch (IOException e) {
            log.warn("Failed to read bootstrap file: {}", filePath, e);
          }
        } else {
          log.warn("Bootstrap file not found, skipping gracefully: {}", filePath);
        }
      }
    }

    // 2. 加载绑定的 Skill 描述 / SKILL.md
    List<String> skillNames = profile.getSkills();
    if (skillNames != null && !skillNames.isEmpty()) {
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
          if (!content.isBlank()) {
            if (contextBuilder.length() > 0) {
              contextBuilder.append("\n\n");
            }
            contextBuilder
                .append("=== Skill: ")
                .append(skillName.trim())
                .append(" ===\n")
                .append(content.trim());
          }
        } catch (IOException e) {
          throw new IllegalStateException("Failed to read skill file: " + skillPath, e);
        }
      }
    }

    return contextBuilder.toString();
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
