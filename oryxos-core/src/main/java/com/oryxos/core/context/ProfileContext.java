package com.oryxos.core.context;

import com.oryxos.core.model.Profile;

/**
 * 线程隔离的 Agent Profile 上下文容器. 解决无 Profile 入参的工具在执行期间按需获取当前 Agent 配置的诉求.
 *
 * <p>在虚拟线程/工作线程执行 Agent 处理时，由入口处绑定、出口处在 finally 中强制清理，杜绝线程复用泄漏.
 *
 * @author oryxos
 */
public final class ProfileContext {

  private static final ThreadLocal<Profile> CURRENT_PROFILE = new ThreadLocal<>();

  private ProfileContext() {
    // 工具类禁止实例化
  }

  /**
   * 将指定 Profile 绑定至当前线程上下文.
   *
   * @param profile 当前 Agent Profile 配置
   */
  public static void set(Profile profile) {
    CURRENT_PROFILE.set(profile);
  }

  /**
   * 获取当前线程绑定的 Profile.
   *
   * @return 当前 Profile 实例，若未绑定返回 null
   */
  public static Profile get() {
    return CURRENT_PROFILE.get();
  }

  /**
   * 获取当前线程绑定的 Profile（get 的别名便捷方法）.
   *
   * @return 当前 Profile 实例，若未绑定返回 null
   */
  public static Profile current() {
    return CURRENT_PROFILE.get();
  }

  /** 清理当前线程绑定的 Profile 上下文，防止内存泄漏或线程复用串号. */
  public static void clear() {
    CURRENT_PROFILE.remove();
  }
}
