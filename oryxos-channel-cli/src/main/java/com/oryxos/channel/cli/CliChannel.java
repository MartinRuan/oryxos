package com.oryxos.channel.cli;

import org.springframework.stereotype.Component;

/**
 * CLI 控制台交互渠道.
 *
 * @author OryxOS Team
 */
@Component
public class CliChannel {

  /**
   * 获取渠道唯一名称.
   *
   * @return 渠道名称
   */
  public String getChannelName() {
    return "cli";
  }
}
