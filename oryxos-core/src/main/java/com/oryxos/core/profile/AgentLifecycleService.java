package com.oryxos.core.profile;

import com.oryxos.core.config.AgentGenerationProperties;
import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.AgentView;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.session.SessionManager;
import com.oryxos.provider.ProviderService;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态 Agent 文件与运行时生命周期的统一编排服务.
 *
 * @author oryxos
 */
public class AgentLifecycleService {

  private static final String GENERATION_CHANNEL = "agent-generation";
  private static final String GENERATION_USER = "designer";
  private static final String GENERATION_PROFILE = "agent-generator";
  private static final String MARKDOWN_CODE_FENCE = "```";
  private static final int MAX_GENERATION_SENTENCE_LENGTH = 4096;
  private static final List<String> SENSITIVE_KEYS =
      List.of("api_key:", "api-key:", "secret:", "token:", "password:", "url:", "webhook:");
  private final AgentStore store;
  private final AgentLoader loader;
  private final ProfileRegistry registry;
  private final AgentScheduler scheduler;
  private final ProviderService providerService;
  private final SessionManager sessionManager;
  private final AgentGenerationProperties generationProperties;
  private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  /** 创建统一生命周期服务. */
  public AgentLifecycleService(
      AgentStore store,
      AgentLoader loader,
      ProfileRegistry registry,
      AgentScheduler scheduler,
      ProviderService providerService,
      SessionManager sessionManager,
      AgentGenerationProperties generationProperties) {
    this.store = store;
    this.loader = loader;
    this.registry = registry;
    this.scheduler = scheduler;
    this.providerService = providerService;
    this.sessionManager = sessionManager;
    this.generationProperties = generationProperties;
  }

  /** 创建、校验并注册 Agent. */
  public AgentView create(String name, String agentMarkdown) {
    return locked(
        name,
        () -> {
          if (store.exists(name) || registry.exists(name)) {
            throw new OryxException(
                StandardErrorCode.AGENT_ALREADY_EXISTS, "Agent already exists: " + name);
          }
          validateDefinition(name, agentMarkdown);
          store.create(name, agentMarkdown);
          try {
            return register(store.agentDirectory(name));
          } catch (RuntimeException e) {
            scheduler.unregisterProfile(name);
            registry.remove(name);
            store.deleteCreated(name);
            throw e;
          }
        });
  }

  /** 从磁盘目录注册或重注册 Agent，供启动扫描、Watcher 与 API 共用. */
  public AgentView register(Path agentDirectory) {
    String directoryName = fileName(agentDirectory);
    return locked(
        directoryName,
        () -> {
          Profile previous = registry.getProfile(directoryName).orElse(null);
          Profile profile = loader.deriveProfile(agentDirectory);
          try {
            registerRuntime(profile);
            return toView(profile, store.read(directoryName));
          } catch (RuntimeException e) {
            restoreRuntime(directoryName, previous);
            throw e;
          }
        });
  }

  /** 列出工作区中的动态 Agent. */
  public List<AgentView> list() {
    if (!java.nio.file.Files.isDirectory(store.agentsRoot())) {
      return List.of();
    }
    try (java.util.stream.Stream<Path> stream = java.nio.file.Files.list(store.agentsRoot())) {
      return stream
          .filter(java.nio.file.Files::isDirectory)
          .sorted(java.util.Comparator.comparing(this::fileName))
          .map(path -> get(fileName(path)))
          .toList();
    } catch (java.io.IOException e) {
      throw new OryxException(StandardErrorCode.INTERNAL_ERROR, "Failed to list Agents", e);
    }
  }

  /** 获取单个 Agent 的安全定义视图. */
  public AgentView get(String name) {
    return locked(
        name,
        () -> {
          String markdown = store.read(name);
          Profile profile =
              registry
                  .getProfile(name)
                  .orElseGet(() -> loader.deriveProfile(store.agentDirectory(name)));
          return toView(profile, markdown);
        });
  }

  /** 原子更新 Agent 并同步运行时. */
  public AgentView update(String name, String agentMarkdown) {
    return locked(
        name,
        () -> {
          if (!store.exists(name)) {
            throw new OryxException(
                StandardErrorCode.PROFILE_NOT_FOUND, "Agent not found: " + name);
          }
          validateDefinition(name, agentMarkdown);
          Profile previousProfile = registry.getProfile(name).orElse(null);
          String previousMarkdown = store.update(name, agentMarkdown);
          try {
            unregisterRuntime(name, previousProfile);
            return register(store.agentDirectory(name));
          } catch (RuntimeException e) {
            try {
              store.restore(name, previousMarkdown);
            } catch (RuntimeException restoreError) {
              e.addSuppressed(restoreError);
            }
            try {
              restoreRuntime(name, previousProfile);
            } catch (RuntimeException restoreError) {
              e.addSuppressed(restoreError);
            }
            throw e;
          }
        });
  }

  /** 注销并归档完整 Agent 目录. */
  public Path delete(String name) {
    return locked(
        name,
        () -> {
          if (!store.exists(name)) {
            throw new OryxException(
                StandardErrorCode.PROFILE_NOT_FOUND, "Agent not found: " + name);
          }
          Profile previous = registry.getProfile(name).orElse(null);
          unregisterRuntime(name, previous);
          try {
            return store.archive(name);
          } catch (RuntimeException e) {
            restoreRuntime(name, previous);
            throw e;
          }
        });
  }

  /** Watcher 发现目录被移走时只注销运行时，不再操作文件. */
  public void unregister(String name) {
    locked(
        name,
        () -> {
          scheduler.unregisterProfile(name);
          registry.remove(name);
          return null;
        });
  }

  /** 使用专用 Provider 生成并以内存规则校验草稿；该操作不写工作区. */
  public String generate(String sentence) {
    if (sentence == null
        || sentence.isBlank()
        || sentence.length() > MAX_GENERATION_SENTENCE_LENGTH) {
      throw new OryxException(
          StandardErrorCode.INVALID_PARAMETER, "sentence must contain 1-4096 characters");
    }
    Profile generationProfile = new Profile();
    generationProfile.setName(GENERATION_PROFILE);
    generationProfile.setDescription("Agent definition generator");
    generationProfile.setProvider(
        new Profile.ProviderConfig(
            generationProperties.getProvider(), generationProperties.getModel(), 0.2));
    Session session =
        sessionManager.getOrCreate(GENERATION_CHANNEL, GENERATION_USER, GENERATION_PROFILE);
    String prompt =
        """
        请根据下面的一句话需求生成一个完整的 OryxOS AGENT.md。
        只输出 Markdown 文件正文，不要代码围栏或解释。
        文件第一个非空行必须是 ---，frontmatter 结束行也必须是 ---。
        frontmatter 必须严格使用以下字段形状：
        name 是英文字母、数字、下划线或连字符组成的字符串；description 是字符串；
        identity 是包含 agent_name 和 prompt 的对象；
        provider 是包含 name、model、temperature 的对象，不要输出 api_key；
        tools 是 YAML 列表；settings 是包含 max_iterations 和 max_history_turns 的对象。
        不要输出 schedules、notify_channels 或 mcp_servers，除非需求明确要求。
        frontmatter 后必须提供清晰的 Markdown 任务指令正文。
        不要输出代码围栏、<think>、推理过程或任何解释。
        需求：
        """
            + sentence.trim();
    ChatResponse response = providerService.chat(session.getId(), generationProfile, prompt);
    String draft = extractAgentDocument(response.getContent());
    try {
      loader.deriveProfile(draft);
      return draft;
    } catch (RuntimeException e) {
      throw new OryxException(
          StandardErrorCode.AGENT_GENERATION_INVALID,
          "Generated Agent draft is invalid: " + e.getMessage(),
          e);
    }
  }

  private String extractAgentDocument(String content) {
    String normalized = content != null ? content.replace("\r\n", "\n").trim() : "";
    java.util.regex.Matcher delimiter =
        java.util.regex.Pattern.compile("(?m)^---[ \t]*$").matcher(normalized);
    while (delimiter.find()) {
      String candidate = removeTrailingFence(normalized.substring(delimiter.start()).trim());
      try {
        loader.deriveProfile(candidate);
        return candidate;
      } catch (RuntimeException ignored) {
        // MiniMax 推理文本可能含独立分隔线，继续寻找真正的 frontmatter 起点。
      }
    }
    return normalized;
  }

  private String removeTrailingFence(String document) {
    if (document.endsWith(MARKDOWN_CODE_FENCE)) {
      return document
          .substring(0, document.length() - MARKDOWN_CODE_FENCE.length())
          .stripTrailing();
    }
    return document;
  }

  private Profile validateDefinition(String expectedName, String markdown) {
    try {
      Profile profile = loader.deriveProfile(markdown);
      if (!expectedName.equals(profile.getName())) {
        throw new OryxException(
            StandardErrorCode.AGENT_DEFINITION_INVALID,
            "Agent name in AGENT.md must match path name: " + expectedName);
      }
      return profile;
    } catch (OryxException e) {
      if (e.getErrorCode() == StandardErrorCode.AGENT_DEFINITION_INVALID) {
        throw e;
      }
      throw new OryxException(StandardErrorCode.AGENT_DEFINITION_INVALID, e.getMessage(), e);
    }
  }

  private void registerRuntime(Profile profile) {
    registry.register(profile);
    try {
      scheduler.registerProfile(profile);
    } catch (RuntimeException e) {
      registry.remove(profile.getName());
      throw e;
    }
  }

  private void unregisterRuntime(String name, Profile profile) {
    if (profile != null) {
      scheduler.unregisterProfile(profile);
    } else {
      scheduler.unregisterProfile(name);
    }
    registry.remove(name);
  }

  private void restoreRuntime(String name, Profile previous) {
    scheduler.unregisterProfile(name);
    registry.remove(name);
    if (previous != null) {
      registry.register(previous);
      scheduler.registerProfile(previous);
    }
  }

  private AgentView toView(Profile profile, String markdown) {
    Profile.ProviderConfig provider = profile.getProvider();
    return new AgentView(
        profile.getName(),
        profile.getDescription(),
        instructions(markdown),
        new AgentView.ProviderView(
            provider.getName(), provider.getModel(), provider.getTemperature()),
        List.copyOf(profile.getTools()),
        List.copyOf(profile.getMcpServers()),
        profile.getNotifyChannels().stream()
            .map(item -> new AgentView.NotifyChannelView(item.getName(), item.getType()))
            .toList(),
        profile.getSchedules().stream()
            .map(
                item ->
                    new AgentView.ScheduleView(
                        item.getId(), item.getCron(), item.getMessage(), item.getTimezone()))
            .toList(),
        "agents/" + profile.getName() + "/AGENT.md",
        redactSecrets(markdown));
  }

  private String instructions(String markdown) {
    String normalized = markdown.replace("\r\n", "\n");
    int first = normalized.indexOf("---");
    int second = normalized.indexOf("---", first + 3);
    return second >= 0 ? normalized.substring(second + 3).trim() : "";
  }

  private String redactSecrets(String markdown) {
    return markdown
        .lines()
        .map(
            line -> {
              String key = line.stripLeading().toLowerCase(Locale.ROOT);
              boolean sensitive = SENSITIVE_KEYS.stream().anyMatch(key::startsWith);
              boolean environmentPlaceholder = line.contains("${");
              if (sensitive && !environmentPlaceholder) {
                return line.substring(0, line.indexOf(':') + 1) + " ***";
              }
              return line;
            })
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private String fileName(Path path) {
    return java.util.Objects.requireNonNull(path.getFileName(), "path file name").toString();
  }

  private <T> T locked(String name, java.util.function.Supplier<T> work) {
    ReentrantLock lock = locks.computeIfAbsent(name, ignored -> new ReentrantLock());
    lock.lock();
    try {
      return work.get();
    } finally {
      lock.unlock();
      if (!lock.isLocked() && !lock.hasQueuedThreads()) {
        locks.remove(name, lock);
      }
    }
  }
}
