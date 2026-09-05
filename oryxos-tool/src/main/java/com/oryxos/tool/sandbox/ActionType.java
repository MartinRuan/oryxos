package com.oryxos.tool.sandbox;

/**
 * 沙箱动作类型枚举.
 *
 * @author OryxOS Team
 */
public enum ActionType {

  /** 文件读取动作. */
  FILE_READ,

  /** 文件写入动作. */
  FILE_WRITE,

  /** Shell 命令执行动作. */
  SHELL_COMMAND,

  /** HTTP 出站请求动作. */
  HTTP_REQUEST
}
