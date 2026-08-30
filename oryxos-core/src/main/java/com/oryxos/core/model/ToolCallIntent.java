package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 模型返回的工具调用意图值对象.
 *
 * @author oryxos
 */
public class ToolCallIntent implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String id;
  private final String name;
  private final String argumentsJson;

  /**
   * 全参构造器.
   *
   * @param id 工具调用唯一 ID
   * @param name 工具名称
   * @param argumentsJson 模型传入的入参 JSON 字符串
   */
  public ToolCallIntent(String id, String name, String argumentsJson) {
    this.id = id;
    this.name = name;
    this.argumentsJson = argumentsJson;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getArgumentsJson() {
    return argumentsJson;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolCallIntent that = (ToolCallIntent) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(argumentsJson, that.argumentsJson);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, argumentsJson);
  }

  @Override
  public String toString() {
    return "ToolCallIntent{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", argumentsJson='"
        + argumentsJson
        + '\''
        + '}';
  }
}
