package com.oryxos.memory.config;

import com.oryxos.core.OryxTool;
import com.oryxos.memory.LongTermMemory;
import com.oryxos.memory.MemoryService;
import com.oryxos.memory.MemoryTools;
import com.oryxos.memory.impl.MemoryServiceImpl;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * OryxOS Memory 模块自动装配配置类.
 *
 * @author OryxOS Team
 */
@AutoConfiguration
public class MemoryAutoConfiguration {

  /**
   * 注册缺省长期记忆物理存储组件.
   *
   * @return LongTermMemory 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public LongTermMemory longTermMemory() {
    return new LongTermMemory();
  }

  /**
   * 注册缺省记忆系统门面服务.
   *
   * @param longTermMemory 长期记忆物理存储组件
   * @return MemoryService 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public MemoryService memoryService(LongTermMemory longTermMemory) {
    return new MemoryServiceImpl(longTermMemory);
  }

  /**
   * 注册缺省记忆工具集.
   *
   * @param memoryService 记忆门面服务
   * @return MemoryTools 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public MemoryTools memoryTools(MemoryService memoryService) {
    return new MemoryTools(memoryService);
  }

  /**
   * 将 save_memory 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param memoryTools 记忆工具集
   * @return save_memory 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "saveMemoryTool")
  public OryxTool saveMemoryTool(MemoryTools memoryTools) {
    return memoryTools.getSaveMemoryTool();
  }

  /**
   * 将 recall_memory 暴露为独立 OryxTool Bean，供统一 ToolExecutor 纳管.
   *
   * @param memoryTools 记忆工具集
   * @return recall_memory 工具
   */
  @Bean
  @ConditionalOnMissingBean(name = "recallMemoryTool")
  public OryxTool recallMemoryTool(MemoryTools memoryTools) {
    return memoryTools.getRecallMemoryTool();
  }

  /**
   * 暴露记忆工具为 OryxTool 列表，供 ToolRegistry 自动纳管.
   *
   * @param memoryTools 记忆工具集
   * @return OryxTool 集合
   */
  @Bean
  public List<OryxTool> memoryToolsBeans(MemoryTools memoryTools) {
    return memoryTools.getTools();
  }
}
