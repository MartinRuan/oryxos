package com.oryxos.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
   * 向指定分区追加一条记忆条目（强制物理写入，无堆内缓存）.
   *
   * @param content 记忆文本内容
   * @param scope 目标作用域（为 null 时缺省写入 ARCHIVAL）
   */
  public synchronized void append(String content, MemoryScope scope) {
    if (content == null || content.isBlank()) {
      return;
    }
    MemoryScope targetScope = scope != null ? scope : MemoryScope.ARCHIVAL;
    String entry = "- [" + LocalDate.now() + "] " + content.trim();

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
    } else {
      if (archiveIdx >= 0) {
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
    }

    writeFile(updated.toString());
  }

  /**
   * 加载长期记忆全貌（每次重新物理读取无缓存；核心区永远完整，归档区按 4000 字符限制截断）.
   *
   * @return 长期记忆拼装文本
   */
  public String load() {
    ensureFileExists();
    String raw = readFile();
    String core = extractSection(raw, CORE_HEADER);
    String archive = truncateIfNeeded(extractSection(raw, ARCHIVE_HEADER));
    return core + "\n\n" + archive;
  }

  /**
   * 提取核心记忆全量文本（供 PromptBuilder 注入，归档区不包含在内）.
   *
   * @return 核心记忆文本
   */
  public String getCoreMemory() {
    ensureFileExists();
    String raw = readFile();
    return extractSection(raw, CORE_HEADER);
  }

  /**
   * 按关键词在归档记忆区检索匹配条目（大小写无关，核心区不参与检索）.
   *
   * @param keyword 检索关键词
   * @return 命中的记录列表，未命中返回空列表
   */
  public List<String> recallByKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return Collections.emptyList();
    }
    ensureFileExists();
    String raw = readFile();
    String archive = extractSection(raw, ARCHIVE_HEADER);
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
    } else {
      return raw.substring(start).trim();
    }
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
