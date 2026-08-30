package com.oryxos.core.session;

import com.oryxos.core.model.Session;
import java.util.Optional;

/**
 * 会话管理统一契约. 负责会话的生命周期管理、内存暂存与持久化协调.
 *
 * @author oryxos
 */
public interface SessionManager {

  /**
   * 根据三元组（渠道、用户、Profile）获取或创建会话. 会话 ID 的唯一拼接完全收敛在内部.
   *
   * @param channel 接入渠道名称
   * @param user 用户标识
   * @param profileName 关联的 Profile 名称
   * @return Session 实体
   */
  default Session getOrCreate(String channel, String user, String profileName) {
    String sid = generateSessionId(channel, user, profileName);
    return getOrCreate(sid, profileName, channel, user);
  }

  /**
   * 根据会话 ID 获取或创建新会话.
   *
   * @param sessionId 会话标识（若为空自动生成）
   * @param profileName 关联的 Profile 名称
   * @param channel 接入渠道名称
   * @param userId 用户标识
   * @return Session 实体
   */
  Session getOrCreate(String sessionId, String profileName, String channel, String userId);

  /**
   * 统一生成标准会话 ID.
   *
   * @param channel 接入渠道
   * @param user 用户标识
   * @param profileName Profile 名称
   * @return 标准化会话 ID 字符串
   */
  static String generateSessionId(String channel, String user, String profileName) {
    String ch = (channel != null && !channel.isBlank()) ? channel.trim() : "cli";
    String u = (user != null && !user.isBlank()) ? user.trim() : "anonymous";
    String p = (profileName != null && !profileName.isBlank()) ? profileName.trim() : "default";
    return ch + ":" + u + ":" + p;
  }

  /**
   * 根据会话 ID 查询会话.
   *
   * @param sessionId 会话标识
   * @return Session Optional
   */
  Optional<Session> get(String sessionId);

  /**
   * 保存或更新会话状态与历史记录.
   *
   * @param session 会话实体
   */
  void save(Session session);

  /**
   * 归档指定会话.
   *
   * @param sessionId 会话标识
   */
  void archive(String sessionId);
}
