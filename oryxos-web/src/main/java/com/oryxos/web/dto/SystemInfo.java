package com.oryxos.web.dto;

import java.util.List;

/**
 * 系统与 Provider 安全运行信息.
 *
 * @param name 应用名称
 * @param version 应用版本
 * @param javaVersion Java 版本
 * @param providers Provider 状态列表
 */
public record SystemInfo(
    String name, String version, String javaVersion, List<ProviderView> providers) {}
