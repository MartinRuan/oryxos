package com.oryxos.channel.feishu;

import org.springframework.stereotype.Component;

/**
 * 飞书 IM 入站交互渠道.
 *
 * @author OryxOS Team
 */
@Component
public class FeishuChannel {

  /**
   * 获取渠道唯一名称.
   *
   * @return 渠道名称
   */
  public String getChannelName() {
    return "feishu";
  }
}
