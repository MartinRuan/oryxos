package com.oryxos.core.context;

import com.oryxos.core.model.Profile;

/**
 * Agent 运行上下文动态加载器契约.
 *
 * <p>负责按需无缓存读取 Bootstrap 文件与 Agent 绑定的 Skill 描述，确保文件修改后即时生效.
 *
 * @author oryxos
 */
public interface ContextLoader {

  /**
   * 按 Profile 动态加载 Bootstrap 与 Skill 上下文文本.
   *
   * @param profile 当前 Agent Profile 配置
   * @return 拼接后的上下文内容，若无内容返回空字符串
   */
  String loadContext(Profile profile);
}
