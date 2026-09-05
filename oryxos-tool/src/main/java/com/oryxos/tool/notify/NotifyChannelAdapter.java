package com.oryxos.tool.notify;

/**
 * 出站通知渠道适配器契约.
 *
 * @author OryxOS Team
 */
public interface NotifyChannelAdapter {

  /**
   * 将指定内容发送到目标渠道.
   *
   * @param target 通知目标及渠道配置
   * @param content 待推送的消息文本
   * @throws RuntimeException 当网络通信失败或远端返回异常时抛出
   */
  void send(NotifyTarget target, String content);
}
