package com.oryxos.storage.repository;

import com.oryxos.storage.entity.SessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 会话持久化仓储接口.
 *
 * @author OryxOS Team
 */
@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

  /**
   * 根据 Profile 名称查询所有关联的会话.
   *
   * @param profileName Profile 名称
   * @return 会话实体列表
   */
  List<SessionEntity> findByProfileName(String profileName);

  /**
   * 根据渠道与用户标识查询会话.
   *
   * @param channel 接入渠道
   * @param userId 用户标识
   * @return 会话实体列表
   */
  List<SessionEntity> findByChannelAndUserId(String channel, String userId);

  /**
   * 根据会话状态查询会话列表.
   *
   * @param status 状态 (ACTIVE / ARCHIVED)
   * @return 会话实体列表
   */
  List<SessionEntity> findByStatus(String status);
}
