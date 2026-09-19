package com.oryxos.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 一句话生成 Agent 使用的专用模型配置.
 *
 * @author oryxos
 */
@ConfigurationProperties(prefix = "oryxos.agent-generation")
public class AgentGenerationProperties {

  private String provider = "minimax";
  private String model = "MiniMax-M2.7";

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }
}
