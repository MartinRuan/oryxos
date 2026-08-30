package com.oryxos.storage.repository;

import com.oryxos.storage.entity.LlmCallEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * LLM 调用审计仓储接口.
 *
 * @author oryxos
 */
@Repository
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, String> {

  /**
   * 根据会话 ID 查询所有关联的 LLM 调用记录.
   *
   * @param sessionId 会话 ID
   * @return 审计记录列表
   */
  List<LlmCallEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

  /**
   * 根据 Provider 名称查询调用记录.
   *
   * @param provider Provider 名称
   * @return 审计记录列表
   */
  List<LlmCallEntity> findByProvider(String provider);
}
