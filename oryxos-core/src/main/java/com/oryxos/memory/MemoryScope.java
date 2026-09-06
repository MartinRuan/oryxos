package com.oryxos.memory;

/**
 * 长期记忆存储作用域枚举.
 *
 * @author OryxOS Team
 */
public enum MemoryScope {

  /** 核心记忆区：用户根本偏好与长久事实，永远全量加载，绝对不截断，不参与关键词检索. */
  CORE,

  /** 归档记忆区：历史记录与流水，超出阈值截断，参与关键词检索. */
  ARCHIVAL
}
