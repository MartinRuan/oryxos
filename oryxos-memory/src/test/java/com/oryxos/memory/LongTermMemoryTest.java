package com.oryxos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 长期记忆物理存储与分区安全测试.
 *
 * @author OryxOS Team
 */
class LongTermMemoryTest {

  @TempDir Path tempDir;

  private Path memoryFilePath;
  private LongTermMemory memory;

  @BeforeEach
  void setUp() {
    memoryFilePath = tempDir.resolve("MEMORY.md");
    memory = new LongTermMemory(memoryFilePath);
  }

  @Test
  @DisplayName("截断只裁归档区_核心记忆一字不能少")
  void 截断只裁归档区_核心记忆一字不能少() {
    memory.append("用户叫小王，偏好用 Java", MemoryScope.CORE);
    for (int i = 0; i < 500; i++) {
      memory.append("归档流水 " + i, MemoryScope.ARCHIVAL); // 把归档区灌到远超 4000 字
    }

    String loaded = memory.load();

    assertTrue(loaded.contains("用户叫小王，偏好用 Java")); // 核心区完整——"始终在场"的底线
    assertFalse(loaded.contains("归档流水 0")); // 归档区最早的内容被裁掉了
    assertTrue(loaded.contains("归档流水 499")); // 保留的是最近的
  }

  @Test
  @DisplayName("写入后立刻可读_不允许有缓存")
  void 写入后立刻可读_不允许有缓存() {
    memory.append("刚记的事", MemoryScope.ARCHIVAL);
    assertTrue(memory.load().contains("刚记的事")); // 同一进程内下一次 load 立即可见
    assertFalse(memory.recallByKeyword("刚记的事").isEmpty()); // 检索同样立即命中
  }

  @Test
  @DisplayName("scope路由到正确区块_缺省写入归档区")
  void scope路由到正确区块_缺省写入归档区() throws IOException {
    memory.append("核心事实A", MemoryScope.CORE);
    memory.append("归档事实B", MemoryScope.ARCHIVAL);
    memory.append("默认事实C", null); // null 缺省写入归档区

    String raw = Files.readString(memoryFilePath);
    int coreHeaderIdx = raw.indexOf("## 核心记忆");
    int archiveHeaderIdx = raw.indexOf("## 归档记忆");
    int factAIdx = raw.indexOf("核心事实A");
    int factBIdx = raw.indexOf("归档事实B");
    int factCIdx = raw.indexOf("默认事实C");

    assertTrue(coreHeaderIdx >= 0);
    assertTrue(archiveHeaderIdx > coreHeaderIdx);
    assertTrue(factAIdx > coreHeaderIdx && factAIdx < archiveHeaderIdx);
    assertTrue(factBIdx > archiveHeaderIdx);
    assertTrue(factCIdx > archiveHeaderIdx);
  }

  @Test
  @DisplayName("recallByKeyword只搜归档区_不区分大小写")
  void recallByKeyword只搜归档区_不区分大小写() {
    memory.append("用户热爱JAVA语言", MemoryScope.CORE);
    memory.append("某天讨论了Java并发编程", MemoryScope.ARCHIVAL);
    memory.append("另一条无涉内容", MemoryScope.ARCHIVAL);

    List<String> hits = memory.recallByKeyword("java");
    assertEquals(1, hits.size());
    assertTrue(hits.get(0).contains("某天讨论了Java并发编程"));
    assertFalse(hits.get(0).contains("用户热爱JAVA语言"));
  }

  @Test
  @DisplayName("文件不存在时自动初始化两分区骨架")
  void 文件不存在自动初始化() throws IOException {
    assertTrue(Files.exists(memoryFilePath));
    String raw = Files.readString(memoryFilePath);
    assertTrue(raw.contains("## 核心记忆"));
    assertTrue(raw.contains("## 归档记忆"));
  }

  @Test
  @DisplayName("提取核心记忆区完整且不含归档区")
  void 仅提取核心记忆() {
    memory.append("核心专属偏好", MemoryScope.CORE);
    memory.append("归档流水日志", MemoryScope.ARCHIVAL);

    String coreOnly = memory.getCoreMemory();
    assertTrue(coreOnly.contains("核心专属偏好"));
    assertFalse(coreOnly.contains("归档流水日志"));
  }
}
