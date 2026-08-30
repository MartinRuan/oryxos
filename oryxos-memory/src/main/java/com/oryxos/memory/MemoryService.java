package com.oryxos.memory;

/**
 * 记忆系统门面契约.
 *
 * @author OryxOS Team
 */
public interface MemoryService {

  /**
   * 检索记忆.
   *
   * @param query 查询内容
   * @return 召回的记忆内容
   */
  String recallMemory(String query);
}
