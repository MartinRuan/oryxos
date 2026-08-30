package com.oryxos.provider.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider 模块配置属性绑定类. 映射 oryxos.provider 与 oryxos.providers 前缀配置.
 *
 * @author oryxos
 */
@ConfigurationProperties(prefix = "oryxos")
public class ProviderProperties {

  private static final int DEFAULT_MAP_CAPACITY = 16;

  /** 默认超时时间（秒），默认 120 秒. */
  private int defaultTimeoutSeconds = 120;

  /** 失败重试次数，默认 2 次. */
  private int defaultMaxRetries = 2;

  /** 多 Provider 显式映射配置表. */
  private Map<String, ProviderConfig> providers = new ConcurrentHashMap<>(DEFAULT_MAP_CAPACITY);

  /**
   * 获取默认请求超时时间.
   *
   * @return 超时时间（秒）
   */
  public int getDefaultTimeoutSeconds() {
    return defaultTimeoutSeconds;
  }

  /**
   * 设置默认请求超时时间.
   *
   * @param defaultTimeoutSeconds 超时时间（秒）
   */
  public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
    this.defaultTimeoutSeconds = defaultTimeoutSeconds;
  }

  /**
   * 获取默认失败重试次数.
   *
   * @return 重试次数
   */
  public int getDefaultMaxRetries() {
    return defaultMaxRetries;
  }

  /**
   * 设置默认失败重试次数.
   *
   * @param defaultMaxRetries 重试次数
   */
  public void setDefaultMaxRetries(int defaultMaxRetries) {
    this.defaultMaxRetries = defaultMaxRetries;
  }

  /**
   * 获取多 Provider 显式配置表.
   *
   * @return 只读 Provider 配置表
   */
  public Map<String, ProviderConfig> getProviders() {
    return Collections.unmodifiableMap(providers);
  }

  /**
   * 设置多 Provider 显式配置表.
   *
   * @param providers Provider 配置映射表
   */
  public void setProviders(Map<String, ProviderConfig> providers) {
    if (providers != null) {
      int initialCapacity = Math.max((int) (providers.size() / 0.75f) + 1, DEFAULT_MAP_CAPACITY);
      this.providers = new ConcurrentHashMap<>(initialCapacity);
      this.providers.putAll(providers);
    } else {
      this.providers = new ConcurrentHashMap<>(DEFAULT_MAP_CAPACITY);
    }
  }

  /** 单个 Provider 详细配置. */
  public static class ProviderConfig {

    private String type = "CLOUD";
    private String defaultModel;
    private List<String> supportedModels = Collections.emptyList();
    private String baseUrl;
    private String apiKey;

    /**
     * 获取 Provider 类型.
     *
     * @return 类型 (CLOUD, LOCAL, MOCK)
     */
    public String getType() {
      return type;
    }

    /**
     * 设置 Provider 类型.
     *
     * @param type 类型 (CLOUD, LOCAL, MOCK)
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * 获取默认模型名称.
     *
     * @return 默认模型名称
     */
    public String getDefaultModel() {
      return defaultModel;
    }

    /**
     * 设置默认模型名称.
     *
     * @param defaultModel 默认模型名称
     */
    public void setDefaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
    }

    /**
     * 获取支持的模型列表.
     *
     * @return 支持的模型列表
     */
    public List<String> getSupportedModels() {
      return Collections.unmodifiableList(supportedModels);
    }

    /**
     * 设置支持的模型列表.
     *
     * @param supportedModels 支持的模型列表
     */
    public void setSupportedModels(List<String> supportedModels) {
      this.supportedModels =
          supportedModels != null ? List.copyOf(supportedModels) : Collections.emptyList();
    }

    /**
     * 获取 API 基础路径.
     *
     * @return API 基础路径
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    /**
     * 设置 API 基础路径.
     *
     * @param baseUrl API 基础路径
     */
    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    /**
     * 获取 API 密钥.
     *
     * @return API 密钥
     */
    public String getApiKey() {
      return apiKey;
    }

    /**
     * 设置 API 密钥.
     *
     * @param apiKey API 密钥
     */
    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }
  }
}
