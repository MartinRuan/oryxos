package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ToolInvocationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Tool 调用审计数据访问接口.
 *
 * @author oryxos
 */
@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, String> {

  /**
   * 根据会话 ID 查询所有工具调用记录.
   *
   * @param sessionId 会话标识
   * @return 工具调用实体列表
   */
  List<ToolInvocationEntity> findBySessionId(String sessionId);

  /**
   * 根据工具名称查询所有工具调用记录.
   *
   * @param toolName 工具名称
   * @return 工具调用实体列表
   */
  List<ToolInvocationEntity> findByToolName(String toolName);
}
