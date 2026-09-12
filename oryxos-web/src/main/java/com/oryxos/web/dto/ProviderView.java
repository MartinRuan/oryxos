package com.oryxos.web.dto;

import java.util.List;

/**
 * 不含凭证的 Provider 状态视图.
 *
 * @param name Provider 名称
 * @param type Provider 类型
 * @param defaultModel 默认模型
 * @param supportedModels 支持模型
 * @param available 当前是否可用
 */
public record ProviderView(
    String name,
    String type,
    String defaultModel,
    List<String> supportedModels,
    boolean available) {}
