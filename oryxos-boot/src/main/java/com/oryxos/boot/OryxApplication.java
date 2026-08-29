package com.oryxos.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OryxOS Spring Boot 启动引导入口.
 *
 * @author OryxOS Team
 */
@SpringBootApplication(scanBasePackages = "com.oryxos")
public class OryxApplication {

  private static final Logger log = LoggerFactory.getLogger(OryxApplication.class);

  /**
   * 应用程序入口 main 方法.
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    log.info("Starting OryxOS Agent OS Runtime Engine...");
    SpringApplication.run(OryxApplication.class, args);
    log.info("OryxOS successfully initialized and listening for requests.");
  }
}
