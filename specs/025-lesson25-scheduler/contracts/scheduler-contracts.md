# Scheduler Contracts

## 1. Java API Contracts

### 1.1 `com.oryxos.core.scheduler.AgentScheduler`
```java
package com.oryxos.core.scheduler;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Profile.ScheduleConfig;
import java.util.concurrent.locks.Lock;

/**
 * Agent 定时任务调度器统一契约与实现.
 *
 * <p>负责在运行时读取所有 Profile 的定时任务配置，将其动态注册至 Spring TaskScheduler.
 * 到点自动获取任务独占锁，通过 SessionManager 与 AgentService 触发 ReAct 执行流.
 */
public class AgentScheduler {

  /**
   * 注册所有已加载 Profile 的定时任务规则.
   */
  public void registerAll();

  /**
   * 触发单次任务执行（具备防重叠锁控制、会话管理与异常隔离保护）.
   *
   * @param profile 目标 Profile
   * @param sc 调度配置对象
   */
  public void runOnce(Profile profile, ScheduleConfig sc);

  /**
   * 获取指定任务 ID 对应的重入锁（供状态检查与验收测试直接调用）.
   *
   * @param taskId 任务 ID
   * @return 对应的 Lock 实例
   */
  public Lock lockFor(String taskId);
}
```

## 2. Configuration Contracts (in `AGENT.md` frontmatter)

```yaml
schedules:
  - id: daily-weather-check
    cron: "0 0 9 * * ?"
    timezone: "Asia/Shanghai"
    message: "查询今天北京的天气并通过通知渠道播报"
```
