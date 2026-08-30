package com.oryxos.provider;

import com.oryxos.core.model.ProviderDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Provider 显式注册中心. 维护 provider name 到 ChatModel 以及 ProviderDescriptor 的显式映射表. 严格遵循项目宪章原则三：禁用靠扫描
 * Bean 类型区分 Provider，必须显式路由.
 *
 * @author oryxos
 */
@Component
public class ProviderRegistry {

  private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);
  private static final int DEFAULT_CAPACITY = 16;

  private final Map<String, ChatModel> modelMap = new ConcurrentHashMap<>(DEFAULT_CAPACITY);
  private final Map<String, ProviderDescriptor> descriptorMap =
      new ConcurrentHashMap<>(DEFAULT_CAPACITY);

  /**
   * 注册一个 Provider 实例及其元数据描述.
   *
   * @param descriptor 提供商描述符
   * @param chatModel Spring AI ChatModel 实例
   */
  public void register(ProviderDescriptor descriptor, ChatModel chatModel) {
    if (descriptor == null || descriptor.getName() == null || chatModel == null) {
      log.warn(
          "Attempted to register invalid provider: descriptor={}, model={}", descriptor, chatModel);
      return;
    }
    String normalizedName = normalize(descriptor.getName());
    modelMap.put(normalizedName, chatModel);
    descriptorMap.put(normalizedName, descriptor);
    log.info(
        "Provider registered successfully: name={}, defaultModel={}, type={}",
        normalizedName,
        descriptor.getDefaultModel(),
        descriptor.getType());
  }

  /**
   * 注销指定名称的 Provider.
   *
   * @param providerName 提供商名称
   */
  public void unregister(String providerName) {
    if (providerName == null) {
      return;
    }
    String normalizedName = normalize(providerName);
    modelMap.remove(normalizedName);
    descriptorMap.remove(normalizedName);
    log.info("Provider unregistered: {}", normalizedName);
  }

  /**
   * 获取指定 Provider 的 ChatModel 实例.
   *
   * @param providerName 提供商名称
   * @return Optional ChatModel 实例
   */
  public Optional<ChatModel> getModel(String providerName) {
    if (providerName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(modelMap.get(normalize(providerName)));
  }

  /**
   * 获取指定 Provider 的描述符.
   *
   * @param providerName 提供商名称
   * @return Optional ProviderDescriptor
   */
  public Optional<ProviderDescriptor> getDescriptor(String providerName) {
    if (providerName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(descriptorMap.get(normalize(providerName)));
  }

  /**
   * 获取所有已注册 Provider 的描述符列表（只读不可变副本）.
   *
   * @return 提供商描述符集合
   */
  public Collection<ProviderDescriptor> listDescriptors() {
    return Collections.unmodifiableCollection(descriptorMap.values());
  }

  /**
   * 检查指定 Provider 是否已就绪可用.
   *
   * @param providerName 提供商名称
   * @return true 若已注册且就绪
   */
  public boolean isAvailable(String providerName) {
    if (providerName == null) {
      return false;
    }
    return modelMap.containsKey(normalize(providerName));
  }

  private String normalize(String providerName) {
    return providerName.trim().toLowerCase(Locale.ROOT);
  }
}
