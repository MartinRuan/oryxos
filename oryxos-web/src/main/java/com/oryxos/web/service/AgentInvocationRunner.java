package com.oryxos.web.service;

import com.oryxos.core.model.Session;
import com.oryxos.core.service.AgentService;
import com.oryxos.web.exception.AgentInvocationTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 在 Spring Boot 管理的虚拟线程执行器中运行同步 Agent 调用.
 *
 * @author OryxOS Team
 */
@Service
public class AgentInvocationRunner {

  private static final long TIMEOUT_SECONDS = 60L;

  private final AsyncTaskExecutor taskExecutor;
  private final AgentService agentService;

  /**
   * 创建 Agent 调用执行器.
   *
   * @param taskExecutor Spring Boot 自动配置的应用任务执行器
   * @param agentService Agent 服务
   */
  public AgentInvocationRunner(
      @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
          AsyncTaskExecutor taskExecutor,
      AgentService agentService) {
    this.taskExecutor = taskExecutor;
    this.agentService = agentService;
  }

  /**
   * 同步执行 Agent 调用，最多等待 60 秒.
   *
   * @param session 会话
   * @param content 用户消息
   * @return Agent 回复
   */
  public String run(Session session, String content) {
    Future<String> future = taskExecutor.submit(() -> agentService.process(session, content));
    try {
      return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException ex) {
      future.cancel(true);
      throw new AgentInvocationTimeoutException();
    } catch (InterruptedException ex) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Agent invocation interrupted", ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Agent invocation failed", cause);
    }
  }
}
