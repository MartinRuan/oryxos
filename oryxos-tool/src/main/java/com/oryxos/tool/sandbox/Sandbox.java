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

  /**
   * 强制执行沙箱安全白名单校验，若违规抛出异常.
   *
   * @param action 沙箱动作描述
   * @throws RuntimeException 当动作被白名单拒绝时抛出
   */
  void enforce(SandboxAction action);
}
