package com.oryxos.core;

/**
 * OryxTool 核心抽象接口契约.
 *
 * @author OryxOS Team
 */
public interface OryxTool {

  /**
   * 工具唯一标识名称.
   *
   * @return 工具名称
   */
  String getName();

  /**
   * 工具功能描述.
   *
   * @return 工具描述
   */
  String getDescription();
}
