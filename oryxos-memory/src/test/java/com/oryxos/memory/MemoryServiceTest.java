package com.oryxos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.Session;
import com.oryxos.memory.impl.MemoryServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 记忆系统统一门面服务测试.
 *
 * @author OryxOS Team
 */
@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

  @Mock private LongTermMemory longTermMemory;

  private MemoryService memoryService;
  private Session session;

  @BeforeEach
  void setUp() {
    memoryService = new MemoryServiceImpl(longTermMemory);
    session = new Session("sess-001", "ops-agent", "cli", "user-01");
  }

  @Test
  @DisplayName("buildContext返回核心记忆与会话历史的组合_归档区不整体注入")
  void buildContext返回核心记忆与会话历史的组合_归档区不整体注入() {
    when(longTermMemory.getCoreMemory()).thenReturn("## 核心记忆\n- [2026-09-05] 用户叫小王，偏好用 Java");

    session.append(ChatMessage.user("今天天气怎么样？"));
    session.append(ChatMessage.assistant("今天北京天气晴朗。"));

    String context = memoryService.buildContext(session);

    // 核心记忆必定在场
    assertTrue(context.contains("用户叫小王，偏好用 Java"));
    // 归档区流水绝不整体注入
    assertFalse(context.contains("归档流水"));
    assertFalse(context.contains("## 归档记忆"));
  }

  @Test
  @DisplayName("remember与recall正确委托LongTermMemory")
  void remember与recall正确委托() {
    memoryService.remember("记住某事", MemoryScope.CORE);
    verify(longTermMemory).append("记住某事", MemoryScope.CORE);

    when(longTermMemory.recallByKeyword("Java")).thenReturn(List.of("命中条目"));
    List<String> results = memoryService.recall("Java");
    assertEquals(1, results.size());
    assertEquals("命中条目", results.get(0));
  }

  @Test
  @DisplayName("核心记忆为空时返回空字符串不产生冗余噪声")
  void 核心记忆为空返回空() {
    when(longTermMemory.getCoreMemory()).thenReturn("## 核心记忆\n");

    String context = memoryService.buildContext(session);
    assertTrue(context.isBlank());
  }

  @Test
  @DisplayName("load复用LongTermMemory完整加载策略")
  void load复用LongTermMemory完整加载策略() {
    String memory = "## 核心记忆\ncore\n\n## 归档记忆\narchive";
    when(longTermMemory.load()).thenReturn(memory);

    assertEquals(memory, memoryService.load());
    verify(longTermMemory).load();
  }
}
