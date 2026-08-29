package com.oryxos.tool;

import com.oryxos.core.OryxTool;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 工具注册表中心.
 *
 * @author OryxOS Team
 */
@Component
public class ToolRegistry {

  private final Map<String, OryxTool> tools = new ConcurrentHashMap<>();

  /**
   * 注册工具实例.
   *
   * @param tool 工具实例
   */
  public void register(OryxTool tool) {
    tools.put(tool.getName(), tool);
  }

  /**
   * 根据名称获取工具.
   *
   * @param name 工具名称
   * @return 工具 Optional
   */
  public Optional<OryxTool> getTool(String name) {
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
}
