package com.oryxos.core.profile;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Profile 内存索引注册中心. 维护所有已加载 Agent Profile 的映射.
 *
 * @author oryxos
 */
@Component
public class ProfileRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);
  private static final String PATH_TRAVERSAL_DOTS = "..";
  private static final String PATH_SLASH = "/";
  private static final String PATH_BACKSLASH = "\\";

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
  private ProfileLoader profileLoader;

  public ProfileRegistry() {
    // Default constructor
  }

  @Autowired
  public void setProfileLoader(@Lazy ProfileLoader profileLoader) {
    this.profileLoader = profileLoader;
  }

  /**
   * 注册 Profile.
   *
   * @param profile Profile 对象
   */
  public void register(Profile profile) {
    if (profile == null || profile.getName() == null || profile.getName().trim().isEmpty()) {
      throw new IllegalArgumentException("Profile or profile name must not be null or blank");
    }
    profiles.put(profile.getName().trim(), profile);
  }

  /**
   * 按名称获取 Profile Optional.
   *
   * @param name Profile 名称
   * @return Optional of Profile
   */
  public Optional<Profile> getProfile(String name) {
    if (name == null || name.trim().isEmpty()) {
      return Optional.empty();
    }
    String normalizedName = name.trim();
    Profile profile = profiles.get(normalizedName);
    if (profile != null) {
      return Optional.of(profile);
    }
    profile = tryLoadProfile(normalizedName);
    if (profile != null) {
      profiles.put(normalizedName, profile);
      return Optional.of(profile);
    }
    return Optional.empty();
  }

  private Profile tryLoadProfile(String name) {
    if (name == null
        || name.contains(PATH_TRAVERSAL_DOTS)
        || name.contains(PATH_SLASH)
        || name.contains(PATH_BACKSLASH)) {
      return null;
    }
    ProfileLoader loader =
        this.profileLoader != null ? this.profileLoader : new ProfileLoader(this);
    Path[] candidates =
        new Path[] {
          Paths.get(".oryxos", "agents", name, "AGENT.md"),
          Paths.get(".oryxos", "agents", name, "agent.md"),
          Paths.get(".oryxos", "profiles", name + ".yaml"),
          Paths.get(".oryxos", "profiles", name + ".yml"),
          Paths.get("agents", name, "AGENT.md"),
          Paths.get("agents", name, "agent.md"),
          Paths.get("profiles", name + ".yaml"),
          Paths.get("profiles", name + ".yml")
        };

    for (Path candidate : candidates) {
      if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
        try {
          String content = Files.readString(candidate, StandardCharsets.UTF_8);
          Path fileNamePath = candidate.getFileName();
          String fileName = fileNamePath != null ? fileNamePath.toString() : "";
          if ("agent.md".equalsIgnoreCase(fileName)) {
            content = loader.extractFrontmatter(content);
          }
          Profile loaded = loader.parse(content);
          log.info("Lazily loaded profile [{}] from {}", loaded.getName(), candidate);
          return loaded;
        } catch (IOException | RuntimeException e) {
          log.warn(
              "Failed to lazily load profile [{}] from {}: {}", name, candidate, e.getMessage());
        }
      }
    }
    return null;
  }

  /**
   * 获取必需的 Profile，不存在时抛出异常.
   *
   * @param name Profile 名称
   * @return Profile 实例
   */
  public Profile getRequiredProfile(String name) {
    return getProfile(name)
        .orElseThrow(
            () ->
                new OryxException(
                    StandardErrorCode.PROFILE_NOT_FOUND, "Profile not found: " + name));
  }

  /**
   * 获取所有已注册的 Profile 集合.
   *
   * @return Profile 集合
   */
  public Collection<Profile> listProfiles() {
    return Collections.unmodifiableCollection(profiles.values());
  }

  /**
   * 检查是否包含指定 Profile.
   *
   * @param name Profile 名称
   * @return true 若存在
   */
  public boolean containsProfile(String name) {
    if (name == null) {
      return false;
    }
    return profiles.containsKey(name.trim());
  }

  /** 清空注册表. */
  public void clear() {
    profiles.clear();
  }

  /**
   * 当前注册 Profile 数量.
   *
   * @return 数量
   */
  public int size() {
    return profiles.size();
  }
}
