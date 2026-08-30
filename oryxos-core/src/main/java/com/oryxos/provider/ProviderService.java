package com.oryxos.provider;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.exception.ProviderException;
import java.util.Collection;
import java.util.Optional;

/**
 * LLM Provider 统一门面服务契约. 负责多模型显式寻址、协议转换、Function Calling Schema 适配及调用审计.
 *
 * @author oryxos
 */
public interface ProviderService {

  /**
   * 按 Profile 与 Prompt 发起对话调用，自动关联 Session 审计记录.
   *
   * @param sessionId 会话标识
   * @param profile Agent Profile 配置
   * @param prompt 对话请求 Prompt 上下文
   * @return 统一模型响应
   * @throws ProviderException 当模型寻址失败、网络故障或超时抛出
   */
  ChatResponse chat(String sessionId, Profile profile, ChatRequest prompt);

  /**
   * 按 Profile 与纯文本提示词发起简单对话调用便捷方法.
   *
   * @param sessionId 会话标识
   * @param profile Agent Profile 配置
   * @param textPrompt 文本提示词
   * @return 统一模型响应
   */
  ChatResponse chat(String sessionId, Profile profile, String textPrompt);

  /**
   * 同步调用大模型.
   *
   * @param request 统一对话请求
   * @return 统一模型响应
   * @throws ProviderException 当模型寻址失败、网络故障或超时抛出
   */
  ChatResponse call(ChatRequest request);

  /**
   * 注册或更新 Provider 描述符与底层模型.
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
   * 获取系统中所有已注册的 Provider 描述集合.
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
