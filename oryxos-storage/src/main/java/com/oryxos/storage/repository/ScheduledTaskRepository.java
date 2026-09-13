package com.oryxos.storage.repository;

import com.oryxos.storage.entity.ScheduledTaskEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 定时任务状态仓储.
 *
 * @author oryxos
 */
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTaskEntity, String> {

  /**
   * 按 Agent 与配置键查询任务.
   *
   * @param profileName Agent 名称
   * @param scheduleKey 配置键
   * @return 任务实体
   */
  Optional<ScheduledTaskEntity> findByProfileNameAndScheduleKey(
      String profileName, String scheduleKey);

  /**
   * 查询指定 Agent 的全部任务.
   *
   * @param profileName Agent 名称
   * @return 任务列表
   */
  List<ScheduledTaskEntity> findByProfileName(String profileName);

  /**
   * 查询全部活动任务.
   *
   * @return 排序后的活动任务
   */
  List<ScheduledTaskEntity> findAllByRetiredFalseOrderByProfileNameAscScheduleKeyAsc();
}
