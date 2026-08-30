package com.oryxos.core.model;

/**
 * 对话消息角色类型枚举.
 *
 * @author oryxos
 */
public enum MessageType {

  /** 系统预设指令角色. */
  SYSTEM("system"),

  /** 用户输入角色. */
  USER("user"),

  /** 大模型助手角色. */
  ASSISTANT("assistant"),

  /** 工具执行结果回填角色. */
  TOOL("tool");

  private final String value;

  MessageType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * 根据字符串反向解析消息角色.
   *
   * @param text 角色字符串
   * @return 对应的 MessageType
   */
  public static MessageType fromString(String text) {
    if (text == null) {
      return USER;
    }
    String trimmed = text.trim();
    for (MessageType type : values()) {
      if (type.value.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
        return type;
      }
    }
    return USER;
  }
}
