package com.oryxos.provider;

/**
 * Provider 服务门面契约.
 *
 * @author OryxOS Team
 */
public interface ProviderService {

  /**
   * 获取 Provider 唯一名称.
   *
   * @return Provider 名称
   */
  String getProviderName();
}
