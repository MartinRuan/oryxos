package com.oryxos.tool;

import com.oryxos.core.OryxTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 工具注册表中心.
 *
 * <p>统一管理系统内置 Tool、MCP Tool 以及 Spring Bean 形式的插件 Tool，支持按 Profile 粒度精确过滤.
 *
 * @author OryxOS Team
 */
@Component
public class ToolRegistry {

  private final Map<String, OryxTool> tools = new ConcurrentHashMap<>();

  /** 默认无参构造器. */
  public ToolRegistry() {}

  /**
   * 构造工具注册表并预设初始工具列表.
   *
   * @param initialTools 初始工具列表
   */
  public ToolRegistry(List<OryxTool> initialTools) {
    if (initialTools != null) {
      for (OryxTool tool : initialTools) {
        register(tool);
      }
    }
  }

  /**
   * 注册工具实例.
   *
   * @param tool 工具实例
   */
  public void register(OryxTool tool) {
    if (tool != null && tool.getName() != null) {
      tools.put(tool.getName(), tool);
    }
  }

  /**
   * 判定指定名称的工具是否已注册.
   *
   * @param name 工具名称
   * @return true 若已存在
   */
  public boolean contains(String name) {
    if (name == null) {
      return false;
    }
    return tools.containsKey(name);
  }

  /**
   * 根据名称获取工具.
   *
   * @param name 工具名称
   * @return 工具 Optional
   */
  public Optional<OryxTool> getTool(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(tools.get(name));
  }

  /**
   * 获取所有已注册工具列表.
   *
   * @return 工具集合
   */
  public Collection<OryxTool> getAllTools() {
    return tools.values();
  }

  /**
   * 根据 Profile 声明的 tools 列表，精确过滤出该 Agent 可用的工具子集.
   *
   * @param allowedToolNames Profile 声明允许调用的工具名称集合
   * @return 过滤后的工具子集列表（精确匹配，不多不少）
   */
  public List<OryxTool> filterTools(List<String> allowedToolNames) {
    if (allowedToolNames == null || allowedToolNames.isEmpty()) {
      return Collections.emptyList();
    }
    List<OryxTool> filtered = new ArrayList<>();
    for (String toolName : allowedToolNames) {
      if (toolName != null && tools.containsKey(toolName)) {
        filtered.add(tools.get(toolName));
      }
    }
    return filtered;
  }
}
