package com.oryxos.core.profile;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Profile 内存索引注册中心. 维护所有已加载 Agent Profile 的映射.
 *
 * @author oryxos
 */
@Component
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

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
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(profiles.get(name.trim()));
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
