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
