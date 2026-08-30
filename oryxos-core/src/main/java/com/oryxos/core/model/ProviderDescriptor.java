package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provider 提供商描述符.
 *
 * @author oryxos
 */
public class ProviderDescriptor implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final String type;
  private final String defaultModel;
  private final List<String> supportedModels;
  private final String baseUrl;
  private final String apiKey;

  /**
   * 全参构造器.
   *
   * @param name 提供商唯一标识
   * @param type 类型 (CLOUD, LOCAL, MOCK)
   * @param defaultModel 默认模型
   * @param supportedModels 支持的模型列表
   * @param baseUrl API 基础路径
   * @param apiKey API 访问凭据
   */
  public ProviderDescriptor(
      String name,
      String type,
      String defaultModel,
      List<String> supportedModels,
      String baseUrl,
      String apiKey) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.type = type != null ? type : "CLOUD";
    this.defaultModel = defaultModel != null ? defaultModel : "";
    this.supportedModels =
        supportedModels != null ? List.copyOf(supportedModels) : Collections.emptyList();
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  /**
   * 创建 Builder 构建器.
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getDefaultModel() {
    return defaultModel;
  }

  public List<String> getSupportedModels() {
    return Collections.unmodifiableList(supportedModels);
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProviderDescriptor that = (ProviderDescriptor) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "ProviderDescriptor{"
        + "name='"
        + name
        + '\''
        + ", type='"
        + type
        + '\''
        + ", defaultModel='"
        + defaultModel
        + '\''
        + ", supportedModels="
        + supportedModels
        + ", baseUrl='"
        + baseUrl
        + '\''
        + '}';
  }

  /** Builder 构造器. */
  public static final class Builder {
    private String name;
    private String type = "CLOUD";
    private String defaultModel;
    private List<String> supportedModels = Collections.emptyList();
    private String baseUrl;
    private String apiKey;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder defaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
      return this;
    }

    /**
     * 设置支持的模型列表.
     *
     * @param supportedModels 模型列表
     * @return Builder 实例
     */
    public Builder supportedModels(List<String> supportedModels) {
      this.supportedModels =
          supportedModels != null ? List.copyOf(supportedModels) : Collections.emptyList();
      return this;
    }

    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /**
     * 构建 ProviderDescriptor 实例.
     *
     * @return ProviderDescriptor 对象
     */
    public ProviderDescriptor build() {
      return new ProviderDescriptor(name, type, defaultModel, supportedModels, baseUrl, apiKey);
    }
  }
}
