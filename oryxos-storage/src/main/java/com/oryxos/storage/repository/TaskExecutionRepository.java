package com.oryxos.storage.repository;

import com.oryxos.storage.entity.TaskExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 定时任务执行历史仓储.
 *
 * @author oryxos
 */
public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, Long> {

  /**
   * 按任务查询倒序执行历史.
   *
   * @param scheduleId 稳定任务 ID
   * @return 倒序执行历史
   */
  List<TaskExecutionEntity> findByScheduleIdOrderByStartedAtDesc(String scheduleId);
}
