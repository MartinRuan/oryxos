# Phase 1 Contract: ProviderService 接口契约规范

**Feature**: `001-llm-provider-integration`
**Date**: 2026-08-29

---

## 1. Java 接口契约 (`com.oryxos.provider.ProviderService`)

```java
package com.oryxos.provider;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.ProviderDescriptor;
import java.util.Collection;
import java.util.Optional;

/**
 * LLM Provider 统一门面服务契约.
 * 负责多模型显式寻址、协议转换、Function Calling Schema 适配及调用审计.
 */
public interface ProviderService {

  /**
   * 同步调用大模型.
   *
   * @param request 统一对话请求
   * @return 统一模型响应
   * @throws ProviderException 当模型寻址失败、网络故障或超时抛出
   */
  ChatResponse call(ChatRequest request);

  /**
   * 注册或更新 Provider.
   *
   * @param descriptor 提供商描述符
   */
  void registerProvider(ProviderDescriptor descriptor);

  /**
   * 根据名称获取已注册的 Provider 描述信息.
   *
   * @param providerName Provider 名称
   * @return Provider 描述 Optional
   */
  Optional<ProviderDescriptor> getProvider(String providerName);

  /**
   * 获取系统中所有已注册的 Provider 列表.
   *
   * @return Provider 描述集合
   */
  Collection<ProviderDescriptor> listProviders();

  /**
   * 检查指定 Provider 是否已就绪.
   *
   * @param providerName Provider 名称
   * @return true 若存在且配置就绪
   */
  boolean isProviderAvailable(String providerName);
}
```

---

## 2. 异常契约 (`ProviderException`)

```java
package com.oryxos.provider.exception;

import com.oryxos.core.exception.ErrorCode;
import com.oryxos.core.exception.OryxException;

public class ProviderException extends OryxException {

  private final String provider;
  private final String model;

  public ProviderException(ErrorCode errorCode, String provider, String model, String message) {
    super(errorCode, message);
    this.provider = provider;
    this.model = model;
  }

  public ProviderException(ErrorCode errorCode, String provider, String model, String message, Throwable cause) {
    super(errorCode, message, cause);
    this.provider = provider;
    this.model = model;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }
}
```

---

## 3. Provider 错误码规范

| 错误码枚举 | 数字码 | 说明 |
| :--- | :--- | :--- |
| `PROVIDER_NOT_FOUND` | 40410 | 指定的 Provider 名称未在系统中注册 |
| `PROVIDER_AUTH_FAILED` | 40110 | Provider API Key 缺失、未设置环境变量或认证失败 |
| `PROVIDER_TIMEOUT` | 50410 | 模型调用超出 120 秒超时阈值 |
| `PROVIDER_RATE_LIMIT` | 42910 | 触发远程服务配额限制或 429 请求过多 |
| `PROVIDER_RESPONSE_MALFORMED` | 50210 | 模型返回畸变 JSON 或无法解析的参数格式 |
| `PROVIDER_SERVICE_UNAVAILABLE` | 50310 | 远程服务 5xx 故障且重试耗尽 |
