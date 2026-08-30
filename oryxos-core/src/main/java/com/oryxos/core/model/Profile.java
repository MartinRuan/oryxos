package com.oryxos.core.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时配置 Profile 领域模型. 承载 Agent 的全字段配置与元数据.
 *
 * @author oryxos
 */
public class Profile implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private String description;
  private Identity identity;
  private ProviderConfig provider;
  private List<String> tools = new ArrayList<>();
  private List<String> skills = new ArrayList<>();
  private List<String> mcpServers = new ArrayList<>();
  private List<ChannelConfig> channels = new ArrayList<>();
  private List<String> notifyChannels = new ArrayList<>();
  private List<ScheduleConfig> schedules = new ArrayList<>();
  private List<String> bootstrap = new ArrayList<>();
  private Settings settings = new Settings();

  /** 默认无参构造器. */
  public Profile() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Identity getIdentity() {
    return identity;
  }

  public void setIdentity(Identity identity) {
    this.identity = identity;
  }

  public ProviderConfig getProvider() {
    return provider;
  }

  public void setProvider(ProviderConfig provider) {
    this.provider = provider;
  }

  public List<String> getTools() {
    return tools != null ? tools : Collections.emptyList();
  }

  public void setTools(List<String> tools) {
    this.tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
  }

  public List<String> getSkills() {
    return skills != null ? skills : Collections.emptyList();
  }

  public void setSkills(List<String> skills) {
    this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
  }

  public List<String> getMcpServers() {
    return mcpServers != null ? mcpServers : Collections.emptyList();
  }

  public void setMcpServers(List<String> mcpServers) {
    this.mcpServers = mcpServers != null ? new ArrayList<>(mcpServers) : new ArrayList<>();
  }

  public List<ChannelConfig> getChannels() {
    return channels != null ? channels : Collections.emptyList();
  }

  public void setChannels(List<ChannelConfig> channels) {
    this.channels = channels != null ? new ArrayList<>(channels) : new ArrayList<>();
  }

  public List<String> getNotifyChannels() {
    return notifyChannels != null ? notifyChannels : Collections.emptyList();
  }

  public void setNotifyChannels(List<String> notifyChannels) {
    this.notifyChannels =
        notifyChannels != null ? new ArrayList<>(notifyChannels) : new ArrayList<>();
  }

  public List<ScheduleConfig> getSchedules() {
    return schedules != null ? schedules : Collections.emptyList();
  }

  public void setSchedules(List<ScheduleConfig> schedules) {
    this.schedules = schedules != null ? new ArrayList<>(schedules) : new ArrayList<>();
  }

  public List<String> getBootstrap() {
    return bootstrap != null ? bootstrap : Collections.emptyList();
  }

  public void setBootstrap(List<String> bootstrap) {
    this.bootstrap = bootstrap != null ? new ArrayList<>(bootstrap) : new ArrayList<>();
  }

  public Settings getSettings() {
    return settings != null ? settings : new Settings();
  }

  public void setSettings(Settings settings) {
    this.settings = settings != null ? settings : new Settings();
  }

  /**
   * 获取配置的 Provider 名称便捷方法.
   *
   * @return Provider 名称
   */
  public String getProviderName() {
    return provider != null ? provider.getName() : null;
  }

  /**
   * 获取配置的模型名称便捷方法.
   *
   * @return 模型名称
   */
  public String getModelName() {
    return provider != null ? provider.getModel() : null;
  }

  /** Agent 身份信息. */
  public static class Identity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String agentName;
    private String prompt;

    /** 默认无参构造器. */
    public Identity() {}

    /**
     * 构造身份信息.
     *
     * @param agentName Agent 显示名称
     * @param prompt 人格与指令 Prompt
     */
    public Identity(String agentName, String prompt) {
      this.agentName = agentName;
      this.prompt = prompt;
    }

    public String getAgentName() {
      return agentName;
    }

    public void setAgentName(String agentName) {
      this.agentName = agentName;
    }

    public String getPrompt() {
      return prompt;
    }

    public void setPrompt(String prompt) {
      this.prompt = prompt;
    }
  }

  /** Provider 配置参数. */
  public static class ProviderConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String model;
    private Double temperature;
    private String apiKey;
    private String baseUrl;

    /** 默认无参构造器. */
    public ProviderConfig() {}

    /**
     * 构造 Provider 参数.
     *
     * @param name 提供商名称
     * @param model 模型名称
     * @param temperature 生成温度
     */
    public ProviderConfig(String name, String model, Double temperature) {
      this.name = name;
      this.model = model;
      this.temperature = temperature;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
  }

  /** 渠道配置. */
  public static class ChannelConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String type;
    private Map<String, Object> config;

    /** 默认无参构造器. */
    public ChannelConfig() {}

    /**
     * 构造渠道配置.
     *
     * @param name 渠道名称
     */
    public ChannelConfig(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public void setConfig(Map<String, Object> config) {
      this.config = config;
    }
  }

  /** 调度配置. */
  public static class ScheduleConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private String cron;
    private String message;
    private String timezone;

    /** 默认无参构造器. */
    public ScheduleConfig() {}

    /**
     * 构造调度配置.
     *
     * @param cron Cron 表达式
     * @param message 触发消息
     * @param timezone 时区
     */
    public ScheduleConfig(String cron, String message, String timezone) {
      this.cron = cron;
      this.message = message;
      this.timezone = timezone;
    }

    public String getCron() {
      return cron;
    }

    public void setCron(String cron) {
      this.cron = cron;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getTimezone() {
      return timezone;
    }

    public void setTimezone(String timezone) {
      this.timezone = timezone;
    }
  }

  /** 运行时控制配置. */
  public static class Settings implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maxIterations = 10;
    private int maxHistoryTurns = 20;

    /** 默认无参构造器. */
    public Settings() {}

    /**
     * 构造控制配置.
     *
     * @param maxIterations 最大循环迭代次数
     * @param maxHistoryTurns 最大保留历史轮数
     */
    public Settings(int maxIterations, int maxHistoryTurns) {
      this.maxIterations = maxIterations;
      this.maxHistoryTurns = maxHistoryTurns;
    }

    public int getMaxIterations() {
      return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
      this.maxIterations = maxIterations;
    }

    public int getMaxHistoryTurns() {
      return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(int maxHistoryTurns) {
      this.maxHistoryTurns = maxHistoryTurns;
    }
  }

  /**
   * 创建 Profile Builder.
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Profile Builder. */
  public static final class Builder {
    private final Profile profile = new Profile();

    public Builder name(String name) {
      profile.setName(name);
      return this;
    }

    public Builder description(String description) {
      profile.setDescription(description);
      return this;
    }

    public Builder identity(Identity identity) {
      profile.setIdentity(identity);
      return this;
    }

    public Builder provider(ProviderConfig provider) {
      profile.setProvider(provider);
      return this;
    }

    public Builder tools(List<String> tools) {
      profile.setTools(tools);
      return this;
    }

    public Builder skills(List<String> skills) {
      profile.setSkills(skills);
      return this;
    }

    public Builder mcpServers(List<String> mcpServers) {
      profile.setMcpServers(mcpServers);
      return this;
    }

    public Builder channels(List<ChannelConfig> channels) {
      profile.setChannels(channels);
      return this;
    }

    public Builder notifyChannels(List<String> notifyChannels) {
      profile.setNotifyChannels(notifyChannels);
      return this;
    }

    public Builder schedules(List<ScheduleConfig> schedules) {
      profile.setSchedules(schedules);
      return this;
    }

    public Builder bootstrap(List<String> bootstrap) {
      profile.setBootstrap(bootstrap);
      return this;
    }

    public Builder settings(Settings settings) {
      profile.setSettings(settings);
      return this;
    }

    public Profile build() {
      return profile;
    }
  }
}
