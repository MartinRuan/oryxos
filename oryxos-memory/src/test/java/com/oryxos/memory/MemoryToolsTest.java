package com.oryxos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.OryxTool;
import com.oryxos.core.model.ToolResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 长期记忆 Agent 工具集测试.
 *
 * @author OryxOS Team
 */
@ExtendWith(MockitoExtension.class)
class MemoryToolsTest {

  @Mock private MemoryService memoryService;

  private MemoryTools memoryTools;

  @BeforeEach
  void setUp() {
    memoryTools = new MemoryTools(memoryService);
  }

  @Test
  @DisplayName("scope缺省写归档")
  void scope缺省写归档() {
    String reply = memoryTools.saveMemory("日常流水记录", null);
    assertEquals("已记住", reply);
    verify(memoryService).remember("日常流水记录", MemoryScope.ARCHIVAL);

    // 字符串 "archival" 与空字符串同样安全降级归档
    memoryTools.saveMemory("另一条流水", "archival");
    verify(memoryService).remember("另一条流水", MemoryScope.ARCHIVAL);
  }

  @Test
  @DisplayName("关键词未命中返回友好提示不报错")
  void 关键词未命中返回友好提示不报错() {
    when(memoryService.recall("不存在的秘密")).thenReturn(Collections.emptyList());

    String reply = memoryTools.recallMemory("不存在的秘密");
    assertEquals("没有找到相关记忆", reply);
  }

  @Test
  @DisplayName("显式指定 core 正确路由至核心记忆区")
  void 显式指定core正确路由() {
    String reply = memoryTools.saveMemory("用户叫小王", "core");
    assertEquals("已记住", reply);
    verify(memoryService).remember("用户叫小王", MemoryScope.CORE);

    // 大小写不敏感 "CORE"
    memoryTools.saveMemory("用户偏好Java", "CORE");
    verify(memoryService).remember("用户偏好Java", MemoryScope.CORE);
  }

  @Test
  @DisplayName("关键词命中时多行拼接返回")
  void 关键词命中多行拼接返回() {
    when(memoryService.recall("Java"))
        .thenReturn(List.of("- [2026-09-05] 偏好用 Java", "- [2026-09-05] 讨论 Java 并发"));

    String reply = memoryTools.recallMemory("Java");
    assertTrue(reply.contains("偏好用 Java"));
    assertTrue(reply.contains("讨论 Java 并发"));
  }

  @Test
  @DisplayName("OryxTool契约适配正常执行")
  void oryxTool适配执行正常() {
    OryxTool saveTool = memoryTools.getSaveMemoryTool();
    assertEquals("save_memory", saveTool.getName());
    assertFalse(saveTool.getDescription().isBlank());
    assertTrue(saveTool.getInputSchema().contains("content"));

    ToolResult saveResult = saveTool.execute("{\"content\":\"记住密码规则\",\"scope\":\"core\"}");
    assertTrue(saveResult.isSuccess());
    assertEquals("已记住", saveResult.getContent());
    verify(memoryService).remember("记住密码规则", MemoryScope.CORE);

    OryxTool recallTool = memoryTools.getRecallMemoryTool();
    assertEquals("recall_memory", recallTool.getName());
    when(memoryService.recall("密码")).thenReturn(List.of("- [2026-09-05] 密码规则为强密码"));

    ToolResult recallResult = recallTool.execute("{\"keyword\":\"密码\"}");
    assertTrue(recallResult.isSuccess());
    assertTrue(recallResult.getContent().contains("密码规则为强密码"));
  }
}
