package com.oryxos.cli.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * CLI 重命令 Spring 容器引导类.
 *
 * <p>扫描 com.oryxos 包下所有组件与自动配置.
 *
 * @author OryxOS Team
 */
@SpringBootApplication(
    scanBasePackages = "com.oryxos",
    excludeName = {
      "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAutoConfiguration",
      "org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration"
    })
public class SpringContextLauncher {

  private static ConfigurableApplicationContext context;

  /**
   * 获取或初始化全局 Spring ApplicationContext.
   *
   * @param args 命令行参数
   * @return 激活的 ApplicationContext
   */
  public static synchronized ConfigurableApplicationContext getOrCreateContext(String... args) {
    if (context == null || !context.isActive()) {
      context =
          SpringApplication.run(SpringContextLauncher.class, args != null ? args : new String[0]);
    }
    return context;
  }

  /** 关闭并清理 Spring ApplicationContext. */
  public static synchronized void closeContext() {
    if (context != null && context.isActive()) {
      context.close();
      context = null;
    }
  }
}
