package com.oryxos.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Session;
import com.oryxos.core.service.AgentService;
import com.oryxos.provider.exception.ProviderErrorCode;
import com.oryxos.provider.exception.ProviderException;
import com.oryxos.web.exception.AgentInvocationTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;

class AgentInvocationRunnerTest {

  private AsyncTaskExecutor taskExecutor;
  private AgentService agentService;
  private Future<String> future;
  private AgentInvocationRunner runner;
  private Session session;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    taskExecutor = mock(AsyncTaskExecutor.class);
    agentService = mock(AgentService.class);
    future = mock(Future.class);
    when(taskExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
    runner = new AgentInvocationRunner(taskExecutor, agentService);
    session = new Session("web:user:ops", "ops", "web", "user");
  }

  @Test
  @DisplayName("同步等待60秒内返回Agent结果")
  void 同步等待60秒内返回Agent结果() throws Exception {
    when(future.get(60, TimeUnit.SECONDS)).thenReturn("reply");

    assertEquals("reply", runner.run(session, "hello"));
    verify(future).get(60, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("超过60秒取消任务并抛统一超时异常")
  void 超过60秒取消任务并抛统一超时异常() throws Exception {
    when(future.get(60, TimeUnit.SECONDS)).thenThrow(new TimeoutException("slow"));

    assertThrows(AgentInvocationTimeoutException.class, () -> runner.run(session, "hello"));
    verify(future).cancel(true);
  }

  @Test
  @DisplayName("Provider运行时异常保持原类型向上抛出")
  void Provider运行时异常保持原类型向上抛出() throws Exception {
    ProviderException providerException =
        new ProviderException(
            ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE, "deepseek", "deepseek-chat", "offline");
    when(future.get(60, TimeUnit.SECONDS)).thenThrow(new ExecutionException(providerException));

    ProviderException thrown =
        assertThrows(ProviderException.class, () -> runner.run(session, "hello"));
    assertSame(providerException, thrown);
  }

  @Test
  @DisplayName("等待线程中断时恢复中断标志并取消任务")
  void 等待线程中断时恢复中断标志并取消任务() throws Exception {
    when(future.get(60, TimeUnit.SECONDS)).thenThrow(new InterruptedException("stop"));

    try {
      assertThrows(IllegalStateException.class, () -> runner.run(session, "hello"));
      assertTrue(Thread.currentThread().isInterrupted());
      verify(future).cancel(true);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @DisplayName("一次请求只调用一次AgentService")
  void 一次请求只调用一次AgentService() {
    AgentService directAgentService = mock(AgentService.class);
    when(directAgentService.process(session, "hello")).thenReturn("reply");
    AsyncTaskExecutor directExecutor = mock(AsyncTaskExecutor.class);
    when(directExecutor.submit(any(java.util.concurrent.Callable.class)))
        .thenAnswer(
            invocation -> {
              FutureTask<String> task = new FutureTask<>(invocation.getArgument(0));
              task.run();
              return task;
            });
    AgentInvocationRunner directRunner =
        new AgentInvocationRunner(directExecutor, directAgentService);

    assertEquals("reply", directRunner.run(session, "hello"));
    verify(directAgentService).process(session, "hello");
  }
}
