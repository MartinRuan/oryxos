package com.oryxos.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长期记忆物理文件存储实现.
 *
 * <p>底层直接操作 .oryxos/memory/MEMORY.md 文件，物理两分区管理，强制零缓存保障写后立读，严格隔离归档截断保护核心记忆.
 *
 * @author OryxOS Team
 */
public final class LongTermMemory {

  private static final String DEFAULT_MEMORY_PATH = ".oryxos/memory/MEMORY.md";
  private static final String CORE_HEADER = "## 核心记忆";
  private static final String ARCHIVE_HEADER = "## 归档记忆";
  private static final int MAX_ARCHIVE_CHARS = 4000;
  private static final char AGENT_MARKER_END = ']';
  private static final String LINE_SEPARATOR_REGEX = "\\R";
  private static final Pattern AGENT_ENTRY_PATTERN =
      Pattern.compile("^(\\s*-\\s+\\[[^]]+])\\s+\\[agent:([^]\\r\\n]+)](?:\\s+(.*))?$");

  private final Path memoryFilePath;

  /** 缺省构造器，使用默认工作区路径 .oryxos/memory/MEMORY.md. */
  public LongTermMemory() {
    this(Paths.get(DEFAULT_MEMORY_PATH));
  }

  /**
   * 指定文件路径构造 LongTermMemory.
   *
   * @param memoryFilePath 物理存储文件路径
   */
  public LongTermMemory(Path memoryFilePath) {
    this.memoryFilePath = Objects.requireNonNull(memoryFilePath, "memoryFilePath must not be null");
    ensureFileExists();
  }

  /**
   * 向指定分区追加一条历史共享记忆（兼容无 Agent 上下文的调用）.
   *
   * @param content 记忆文本内容
   * @param scope 目标作用域（为 null 时缺省写入 ARCHIVAL）
   */
  public void append(String content, MemoryScope scope) {
    append(content, scope, null);
  }

  /**
   * 向指定分区追加一条与 Agent 关联的记忆.
   *
   * @param content 记忆文本内容
   * @param scope 目标作用域（为 null 时缺省写入 ARCHIVAL）
   * @param profileName Agent Profile 名称；为空时写为历史共享记忆
   */
  public synchronized void append(String content, MemoryScope scope, String profileName) {
    if (content == null || content.isBlank()) {
      return;
    }
    MemoryScope targetScope = scope != null ? scope : MemoryScope.ARCHIVAL;
    String entry = "- [" + LocalDate.now() + "]" + agentMarker(profileName) + " " + content.trim();

    ensureFileExists();
    String raw = readFile();

    StringBuilder updated = new StringBuilder();
    int archiveIdx = raw.indexOf(ARCHIVE_HEADER);

    if (targetScope == MemoryScope.CORE) {
      if (archiveIdx >= 0) {
        String corePart = raw.substring(0, archiveIdx).stripTrailing();
        String archivePart = raw.substring(archiveIdx);
        updated.append(corePart).append("\n").append(entry).append("\n\n").append(archivePart);
      } else {
        updated
            .append(raw.stripTrailing())
            .append("\n")
            .append(entry)
            .append("\n\n")
            .append(ARCHIVE_HEADER)
            .append("\n");
      }
    } else if (archiveIdx >= 0) {
      updated.append(raw.stripTrailing()).append("\n").append(entry).append("\n");
    } else {
      updated
          .append(raw.stripTrailing())
          .append("\n\n")
          .append(ARCHIVE_HEADER)
          .append("\n")
          .append(entry)
          .append("\n");
    }

    writeFile(updated.toString());
  }

  /**
   * 加载全部长期记忆（每次重新物理读取无缓存；核心区完整，归档区按 4000 字符限制截断）.
   *
   * @return 长期记忆拼装文本
   */
  public String load() {
    return load(null);
  }

  /**
   * 加载指定 Agent 的长期记忆，同时保留未标记的历史共享条目.
   *
   * @param profileName Agent Profile 名称；为空时加载全部记忆
   * @return 与 Agent 关联的长期记忆拼装文本
   */
  public String load(String profileName) {
    ensureFileExists();
    String raw = readFile();
    String core = filterForProfile(extractSection(raw, CORE_HEADER), profileName);
    String archive =
        truncateIfNeeded(filterForProfile(extractSection(raw, ARCHIVE_HEADER), profileName));
    return core + "\n\n" + archive;
  }

  /**
   * 提取全部核心记忆文本（兼容无 Agent 上下文的调用）.
   *
   * @return 核心记忆文本
   */
  public String getCoreMemory() {
    return getCoreMemory(null);
  }

  /**
   * 提取指定 Agent 与历史共享的核心记忆文本.
   *
   * @param profileName Agent Profile 名称；为空时读取全部核心记忆
   * @return 核心记忆文本
   */
  public String getCoreMemory(String profileName) {
    ensureFileExists();
    return filterForProfile(extractSection(readFile(), CORE_HEADER), profileName);
  }

  /**
   * 在全部归档记忆中按关键词检索（兼容无 Agent 上下文的调用）.
   *
   * @param keyword 检索关键词
   * @return 命中的记录列表，未命中返回空列表
   */
  public List<String> recallByKeyword(String keyword) {
    return recallByKeyword(keyword, null);
  }

  /**
   * 按关键词检索指定 Agent 与历史共享的归档记忆.
   *
   * @param keyword 检索关键词
   * @param profileName Agent Profile 名称；为空时检索全部归档记忆
   * @return 命中的记录列表，未命中返回空列表
   */
  public List<String> recallByKeyword(String keyword, String profileName) {
    if (keyword == null || keyword.isBlank()) {
      return Collections.emptyList();
    }
    ensureFileExists();
    String archive = filterForProfile(extractSection(readFile(), ARCHIVE_HEADER), profileName);
    String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

    return archive
        .lines()
        .map(String::trim)
        .filter(
            line ->
                !line.isBlank()
                    && !line.startsWith("##")
                    && line.toLowerCase(Locale.ROOT).contains(lowerKeyword))
        .toList();
  }

  private String agentMarker(String profileName) {
    if (profileName == null || profileName.isBlank()) {
      return "";
    }
    String normalized = profileName.trim();
    if (normalized.indexOf(AGENT_MARKER_END) >= 0
        || normalized.indexOf('\n') >= 0
        || normalized.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("Agent Profile 名称包含非法记忆标记字符");
    }
    return " [agent:" + normalized + "]";
  }

  private String filterForProfile(String section, String profileName) {
    if (section == null || section.isBlank() || profileName == null || profileName.isBlank()) {
      return section != null ? section : "";
    }
    String target = profileName.trim();
    List<String> visibleLines = new ArrayList<>();
    boolean includeCurrentEntry = true;
    for (String line : section.split(LINE_SEPARATOR_REGEX, -1)) {
      Matcher matcher = AGENT_ENTRY_PATTERN.matcher(line);
      if (matcher.matches()) {
        includeCurrentEntry = target.equals(matcher.group(2));
        if (includeCurrentEntry) {
          String content = matcher.group(3);
          visibleLines.add(matcher.group(1) + (content != null ? " " + content : ""));
        }
        continue;
      }
      if (line.stripLeading().startsWith("- [")) {
        includeCurrentEntry = true;
      }
      if (includeCurrentEntry) {
        visibleLines.add(line);
      }
    }
    return String.join("\n", visibleLines).trim();
  }

  private String truncateIfNeeded(String archiveSection) {
    if (archiveSection == null || archiveSection.length() <= MAX_ARCHIVE_CHARS) {
      return archiveSection != null ? archiveSection : "";
    }
    return archiveSection.substring(archiveSection.length() - MAX_ARCHIVE_CHARS);
  }

  private String extractSection(String raw, String header) {
    if (raw == null || !raw.contains(header)) {
      return "";
    }
    int start = raw.indexOf(header);
    if (CORE_HEADER.equals(header)) {
      int end = raw.indexOf(ARCHIVE_HEADER);
      if (end > start) {
        return raw.substring(start, end).trim();
      }
      return raw.substring(start).trim();
    }
    return raw.substring(start).trim();
  }

  private void ensureFileExists() {
    try {
      if (!Files.exists(memoryFilePath)) {
        Path parent = memoryFilePath.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        String initialContent = CORE_HEADER + "\n\n" + ARCHIVE_HEADER + "\n";
        Files.writeString(memoryFilePath, initialContent, StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("无法初始化长期记忆文件: " + memoryFilePath, e);
    }
  }

  private String readFile() {
    try {
      return Files.readString(memoryFilePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取长期记忆文件失败: " + memoryFilePath, e);
    }
  }

  private void writeFile(String content) {
    try {
      Files.writeString(memoryFilePath, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("写入长期记忆文件失败: " + memoryFilePath, e);
    }
  }
}
