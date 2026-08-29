package com.oryxos.tool.sandbox;

/**
 * 应用层沙箱接口契约.
 *
 * @author OryxOS Team
 */
public interface Sandbox {

  /**
   * 校验动作是否在白名单允许范围内.
   *
   * @param target 目标对象（路径、命令、URL等）
   * @return true 若允许执行
   */
  boolean check(String target);
}
