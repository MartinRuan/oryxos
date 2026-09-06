package com.oryxos.memory.impl;

import com.oryxos.core.model.Session;
import com.oryxos.memory.LongTermMemory;
import com.oryxos.memory.MemoryScope;
import com.oryxos.memory.MemoryService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 记忆系统统一门面服务标准实现.
 *
 * <p>统一收口会话记忆与长期记忆，确保核心记忆始终注入、归档记忆绝不盲目注入.
 *
 * @author OryxOS Team
 */
@Service
public class MemoryServiceImpl implements MemoryService {

  private static final String CORE_HEADER = "## 核心记忆";
  private static final String MEMORY_SECTION_PREFIX = "[长期记忆]";

  private final LongTermMemory longTermMemory;

  /**
   * 构造 MemoryServiceImpl.
   *
   * @param longTermMemory 长期记忆物理存储组件
   */
  public MemoryServiceImpl(LongTermMemory longTermMemory) {
    this.longTermMemory = Objects.requireNonNull(longTermMemory, "longTermMemory must not be null");
  }

  @Override
  public String buildContext(Session session) {
    String coreMemory = longTermMemory.getCoreMemory();
    if (coreMemory.isBlank()) {
      return "";
    }

    String trimmed = coreMemory.trim();
    if (CORE_HEADER.equals(trimmed)) {
      return "";
    }

    return MEMORY_SECTION_PREFIX + "\n" + trimmed;
  }

  @Override
  public void remember(String content, MemoryScope scope) {
    longTermMemory.append(content, scope);
  }

  @Override
  public List<String> recall(String keyword) {
    return longTermMemory.recallByKeyword(keyword);
  }
}
