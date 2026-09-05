package com.oryxos.core.profile;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Profile 加载与解析器. 基于 SnakeYAML 解析 .oryxos/profiles/*.yaml， 支持 ${ENV_VAR} 占位符解析与 Provider 合法性校验.
 *
 * @author oryxos
 */
@Component
public class ProfileLoader {

  private static final Logger log = LoggerFactory.getLogger(ProfileLoader.class);

  private static final String AGENT_MD_FILE = "agent.md";
  private static final String YAML_EXT = ".yaml";
  private static final String YML_EXT = ".yml";
  private static final String FRONTMATTER_DELIMITER = "---";
  private static final int FRONTMATTER_DELIMITER_LENGTH = 3;

  private static final String ENV_PREFIX = "${";
  private static final String ENV_SUFFIX = "}";
  private static final int ENV_PREFIX_LENGTH = 2;
  private static final int MIN_QUOTED_LENGTH = 2;
  private static final char COLON = ':';

  private final ProfileRegistry profileRegistry;

  public ProfileLoader() {
    this.profileRegistry = null;
  }

  public ProfileLoader(ProfileRegistry profileRegistry) {
    this.profileRegistry = profileRegistry;
  }

  /**
   * 扫描指定目录下的所有 YAML 文件并解析加载.
   *
   * @param profilesDir profiles 根目录路径
   * @return 解析成功的 Profile 列表
   */
  public List<Profile> loadProfiles(Path profilesDir) {
    return loadProfiles(profilesDir, Collections.emptySet());
  }

  /**
   * 扫描指定目录下的所有 YAML 文件并校验 Provider 存在性.
   *
   * @param profilesDir profiles 根目录路径
   * @param availableProviders 当前全局已配置的 Provider 标识集合（非空时校验）
   * @return 解析成功并通过校验的 Profile 列表
   */
  public List<Profile> loadProfiles(Path profilesDir, Set<String> availableProviders) {
    if (profilesDir == null || !Files.exists(profilesDir)) {
      log.debug("Profile directory not found: {}", profilesDir);
      return Collections.emptyList();
    }

    List<Profile> loaded = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(profilesDir, 4)) {
      stream
          .filter(Files::isRegularFile)
          .filter(
              path -> {
                Path fileNamePath = path.getFileName();
                if (fileNamePath == null) {
                  return false;
                }
                String fileName = fileNamePath.toString();
                return fileName.endsWith(YAML_EXT)
                    || fileName.endsWith(YML_EXT)
                    || AGENT_MD_FILE.equalsIgnoreCase(fileName);
              })
          .forEach(
              path -> {
                try {
                  Path fileNamePath = path.getFileName();
                  String fileName = fileNamePath != null ? fileNamePath.toString() : "";
                  String content = Files.readString(path, StandardCharsets.UTF_8);
                  if (AGENT_MD_FILE.equalsIgnoreCase(fileName)) {
                    content = extractFrontmatter(content);
                  }
                  Profile profile = parse(content, availableProviders);
                  loaded.add(profile);
                  if (profileRegistry != null) {
                    profileRegistry.register(profile);
                  }
                  log.info("Successfully loaded profile [{}] from {}", profile.getName(), path);
                } catch (Exception ex) {
                  log.error("Failed to load profile from {}: {}", path, ex.getMessage(), ex);
                }
              });
    } catch (IOException e) {
      log.error("Error reading profiles directory: {}", profilesDir, e);
    }

    return loaded;
  }

  /**
   * 解析 YAML 字符串为 Profile 对象.
   *
   * @param yamlContent YAML 字符串
   * @return 解析后的 Profile
   */
  public Profile parse(String yamlContent) {
    return parse(yamlContent, Collections.emptySet());
  }

  /**
   * 解析 YAML 字符串为 Profile 对象并执行合法性校验.
   *
   * @param yamlContent YAML 字符串
   * @param availableProviders 可用 Provider 集合
   * @return 解析后的 Profile
   */
  @SuppressWarnings("unchecked")
  public Profile parse(String yamlContent, Set<String> availableProviders) {
    if (yamlContent == null || yamlContent.trim().isEmpty()) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Profile YAML content must not be null or empty");
    }

    String resolvedContent = resolveEnvironmentVariables(yamlContent);
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Object loaded = yaml.load(resolvedContent);

    if (!(loaded instanceof Map)) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Invalid Profile YAML structure: expected root map");
    }

    Map<String, Object> map = (Map<String, Object>) loaded;
    return convertToProfile(map, availableProviders);
  }

  /** 将 Map 映射为 Profile 实体. */
  private Profile convertToProfile(Map<String, Object> map, Set<String> availableProviders) {
    Profile profile = new Profile();

    String name = getString(map, "name");
    if (name == null || name.trim().isEmpty()) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Profile 'name' field is required");
    }
    profile.setName(name.trim());
    profile.setDescription(getString(map, "description"));

    populateIdentity(profile, map.get("identity"));
    populateProvider(profile, map.get("provider"), availableProviders);
    populateLists(profile, map);
    populateChannels(profile, map.get("channels"));
    populateNotifyChannels(
        profile,
        map.get("notify_channels") != null
            ? map.get("notify_channels")
            : map.get("notifyChannels"));
    populateSchedules(profile, map.get("schedules"));
    populateSettings(profile, map.get("settings"));

    return profile;
  }

  @SuppressWarnings("unchecked")
  private void populateIdentity(Profile profile, Object identityObj) {
    if (identityObj instanceof Map) {
      Map<String, Object> identityMap = (Map<String, Object>) identityObj;
      Profile.Identity identity = new Profile.Identity();
      identity.setAgentName(getString(identityMap, "agent_name", "agentName"));
      identity.setPrompt(getString(identityMap, "prompt"));
      profile.setIdentity(identity);
    }
  }

  @SuppressWarnings("unchecked")
  private void populateProvider(
      Profile profile, Object providerObj, Set<String> availableProviders) {
    if (!(providerObj instanceof Map)) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Profile 'provider' configuration is required");
    }
    Map<String, Object> providerMap = (Map<String, Object>) providerObj;
    String providerName = getString(providerMap, "name");
    if (providerName == null || providerName.trim().isEmpty()) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "Profile 'provider.name' field is required");
    }
    providerName = providerName.trim();

    if (availableProviders != null
        && !availableProviders.isEmpty()
        && !availableProviders.contains(providerName)) {
      throw new OryxException(
          StandardErrorCode.PROVIDER_NOT_FOUND,
          String.format(
              "Profile [%s] references unconfigured provider [%s]. Available providers: %s",
              profile.getName(), providerName, availableProviders));
    }

    Profile.ProviderConfig providerConfig = new Profile.ProviderConfig();
    providerConfig.setName(providerName);
    providerConfig.setModel(getString(providerMap, "model"));
    Object temp = providerMap.get("temperature");
    if (temp instanceof Number) {
      providerConfig.setTemperature(((Number) temp).doubleValue());
    }
    providerConfig.setApiKey(getString(providerMap, "api_key", "apiKey", "api-key"));
    providerConfig.setBaseUrl(getString(providerMap, "base_url", "baseUrl", "base-url"));
    profile.setProvider(providerConfig);
  }

  private void populateLists(Profile profile, Map<String, Object> map) {
    profile.setTools(getStringList(map, "tools"));
    profile.setSkills(getStringList(map, "skills"));
    profile.setMcpServers(getStringList(map, "mcp_servers", "mcpServers"));
    profile.setBootstrap(getStringList(map, "bootstrap"));
  }

  @SuppressWarnings("unchecked")
  private void populateNotifyChannels(Profile profile, Object notifyChannelsObj) {
    if (notifyChannelsObj instanceof List) {
      List<?> list = (List<?>) notifyChannelsObj;
      List<Profile.NotifyChannelConfig> notifyChannels = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map) {
          Map<String, Object> channelMap = (Map<String, Object>) item;
          String name = getString(channelMap, "name");
          String type = getString(channelMap, "type");
          if (type == null || type.trim().isEmpty()) {
            type = "webhook";
          }
          if (name == null || name.trim().isEmpty()) {
            name = type;
          }
          Map<String, String> config = new java.util.HashMap<>();
          for (Map.Entry<String, Object> entry : channelMap.entrySet()) {
            if (entry.getValue() != null) {
              config.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
          }
          Profile.NotifyChannelConfig channelConfig =
              new Profile.NotifyChannelConfig(name, type, config);
          notifyChannels.add(channelConfig);
        } else if (item instanceof String) {
          String str = (String) item;
          Map<String, String> config = new java.util.HashMap<>();
          config.put("url", str);
          Profile.NotifyChannelConfig channelConfig =
              new Profile.NotifyChannelConfig(str, "webhook", config);
          notifyChannels.add(channelConfig);
        }
      }
      profile.setNotifyChannels(notifyChannels);
    }
  }

  @SuppressWarnings("unchecked")
  private void populateChannels(Profile profile, Object channelsObj) {
    if (channelsObj instanceof List) {
      List<?> channelList = (List<?>) channelsObj;
      List<Profile.ChannelConfig> channels = new ArrayList<>();
      for (Object item : channelList) {
        if (item instanceof Map) {
          Map<String, Object> channelMap = (Map<String, Object>) item;
          Profile.ChannelConfig channelConfig = new Profile.ChannelConfig();
          channelConfig.setName(getString(channelMap, "name"));
          channelConfig.setType(getString(channelMap, "type"));
          channelConfig.setConfig(channelMap);
          channels.add(channelConfig);
        } else if (item instanceof String) {
          channels.add(new Profile.ChannelConfig((String) item));
        }
      }
      profile.setChannels(channels);
    }
  }

  @SuppressWarnings("unchecked")
  private void populateSchedules(Profile profile, Object schedulesObj) {
    if (schedulesObj instanceof List) {
      List<?> scheduleList = (List<?>) schedulesObj;
      List<Profile.ScheduleConfig> schedules = new ArrayList<>();
      for (Object item : scheduleList) {
        if (item instanceof Map) {
          Map<String, Object> scheduleMap = (Map<String, Object>) item;
          Profile.ScheduleConfig scheduleConfig = new Profile.ScheduleConfig();
          scheduleConfig.setCron(getString(scheduleMap, "cron"));
          scheduleConfig.setMessage(getString(scheduleMap, "message"));
          scheduleConfig.setTimezone(getString(scheduleMap, "timezone", "zone"));
          schedules.add(scheduleConfig);
        }
      }
      profile.setSchedules(schedules);
    }
  }

  @SuppressWarnings("unchecked")
  private void populateSettings(Profile profile, Object settingsObj) {
    if (settingsObj instanceof Map) {
      Map<String, Object> settingsMap = (Map<String, Object>) settingsObj;
      Profile.Settings settings = new Profile.Settings();
      Object maxIter = settingsMap.get("max_iterations");
      if (maxIter == null) {
        maxIter = settingsMap.get("maxIterations");
      }
      if (maxIter instanceof Number) {
        settings.setMaxIterations(((Number) maxIter).intValue());
      }
      Object maxTurns = settingsMap.get("max_history_turns");
      if (maxTurns == null) {
        maxTurns = settingsMap.get("maxHistoryTurns");
      }
      if (maxTurns instanceof Number) {
        settings.setMaxHistoryTurns(((Number) maxTurns).intValue());
      }
      profile.setSettings(settings);
    }
  }

  private static final Map<String, String> DOT_ENV_CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile boolean dotEnvLoaded = false;

  private static void ensureDotEnvLoaded() {
    if (dotEnvLoaded) {
      return;
    }
    synchronized (DOT_ENV_CACHE) {
      if (dotEnvLoaded) {
        return;
      }
      String userDir = System.getProperty("user.dir", ".");
      List<Path> candidates =
          List.of(
              Path.of(".env"),
              Path.of(".oryxos", ".env"),
              Path.of(userDir, ".env"),
              Path.of(userDir, ".oryxos", ".env"));
      for (Path p : candidates) {
        if (Files.isRegularFile(p)) {
          try {
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : lines) {
              String s = line.trim();
              if (!s.isEmpty() && !s.startsWith("#") && s.indexOf('=') > 0) {
                int eq = s.indexOf('=');
                String k = s.substring(0, eq).trim();
                String v = s.substring(eq + 1).trim();
                v = stripQuotes(v);
                DOT_ENV_CACHE.putIfAbsent(k, v);
                String existingProp = System.getProperty(k);
                if (existingProp == null || existingProp.isBlank()) {
                  System.setProperty(k, v);
                }
              }
            }
          } catch (Exception e) {
            log.warn("Failed to load environment file: {}", p, e);
          }
        }
      }
      dotEnvLoaded = true;
    }
  }

  /** 解析文本中的 ${ENV_VAR} 与 ${ENV_VAR:default} 占位符（线性扫描防止 ReDoS）. */
  public String resolveEnvironmentVariables(String text) {
    if (text == null || text.isEmpty() || !text.contains(ENV_PREFIX)) {
      return text;
    }
    ensureDotEnvLoaded();
    StringBuilder sb = new StringBuilder();
    int cursor = 0;
    while (cursor < text.length()) {
      int start = text.indexOf(ENV_PREFIX, cursor);
      if (start < 0) {
        sb.append(text.substring(cursor));
        break;
      }
      sb.append(text, cursor, start);
      int end = text.indexOf(ENV_SUFFIX, start + ENV_PREFIX_LENGTH);
      if (end < 0) {
        sb.append(text.substring(start));
        break;
      }
      String expr = text.substring(start + ENV_PREFIX_LENGTH, end);
      String varName = expr;
      String defaultVal = null;
      int colonIdx = expr.indexOf(COLON);
      if (colonIdx >= 0) {
        varName = expr.substring(0, colonIdx);
        defaultVal = expr.substring(colonIdx + 1);
      }
      String envVal = System.getenv(varName);
      if (envVal == null || envVal.isBlank()) {
        envVal = System.getProperty(varName);
      }
      if (envVal == null || envVal.isBlank()) {
        envVal = DOT_ENV_CACHE.get(varName);
      }
      if (envVal != null && !envVal.isBlank()) {
        sb.append(envVal);
      } else if (defaultVal != null) {
        sb.append(defaultVal);
      } else {
        sb.append(ENV_PREFIX).append(expr).append(ENV_SUFFIX);
      }
      cursor = end + 1;
    }
    return sb.toString();
  }

  /** 从 AGENT.md 中提取 frontmatter YAML 内容. */
  public String extractFrontmatter(String content) {
    if (content.startsWith(FRONTMATTER_DELIMITER)) {
      int second = content.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER_LENGTH);
      if (second > FRONTMATTER_DELIMITER_LENGTH) {
        return content.substring(FRONTMATTER_DELIMITER_LENGTH, second).trim();
      }
    }
    return content;
  }

  private String getString(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object val = map.get(key);
      if (val != null) {
        return String.valueOf(val);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<String> getStringList(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object val = map.get(key);
      if (val instanceof List) {
        List<?> raw = (List<?>) val;
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
          if (item != null) {
            result.add(String.valueOf(item));
          }
        }
        return result;
      }
    }
    return Collections.emptyList();
  }

  private static String stripQuotes(String v) {
    if (v != null && v.length() >= MIN_QUOTED_LENGTH) {
      boolean doubleQuoted = v.startsWith("\"") && v.endsWith("\"");
      boolean singleQuoted = v.startsWith("'") && v.endsWith("'");
      if (doubleQuoted || singleQuoted) {
        return v.substring(1, v.length() - 1);
      }
    }
    return v;
  }
}
