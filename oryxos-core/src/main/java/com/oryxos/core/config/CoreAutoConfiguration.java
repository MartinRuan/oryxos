package com.oryxos.core.config;

import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.context.impl.ContextLoaderImpl;
import com.oryxos.core.prompt.PromptBuilder;
import com.oryxos.core.prompt.impl.PromptBuilderImpl;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.react.impl.ReActLoopImpl;
import com.oryxos.core.session.InMemorySessionManager;
import com.oryxos.core.session.SessionManager;
import com.oryxos.core.tool.ToolAuditRecorder;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.core.tool.impl.ToolExecutorImpl;
import com.oryxos.provider.ProviderService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * OryxOS Core 核心模块自动装配配置类.
 *
 * @author oryxos
 */
@AutoConfiguration
public class CoreAutoConfiguration {

  /**
   * 注册缺省 ContextLoader.
   *
   * @return ContextLoader 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ContextLoader contextLoader() {
    return new ContextLoaderImpl();
  }

  /**
   * 注册缺省 SessionManager.
   *
   * @return SessionManager 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public SessionManager sessionManager() {
    return new InMemorySessionManager();
  }

  /**
   * 注册缺省 PromptBuilder.
   *
   * @param contextLoader 上下文加载器
   * @return PromptBuilder 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public PromptBuilder promptBuilder(ContextLoader contextLoader) {
    return new PromptBuilderImpl(contextLoader);
  }

  /**
   * 注册缺省 ToolExecutor.
   *
   * @param tools 可用工具列表（可选）
   * @param auditRecorder 审计记录器（可选）
   * @return ToolExecutor 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ToolExecutor toolExecutor(
      Optional<List<OryxTool>> tools, Optional<ToolAuditRecorder> auditRecorder) {
    return new ToolExecutorImpl(tools.orElse(Collections.emptyList()), auditRecorder.orElse(null));
  }

  /**
   * 注册自实现 ReActLoop 循环引擎.
   *
   * @param promptBuilder Prompt 组装器
   * @param providerService 统一 Provider 门面
   * @param toolExecutor 工具执行器
   * @return ReActLoop 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ReActLoop reActLoop(
      ProviderService providerService, PromptBuilder promptBuilder, ToolExecutor toolExecutor) {
    return new ReActLoopImpl(providerService, promptBuilder, toolExecutor);
  }
}
