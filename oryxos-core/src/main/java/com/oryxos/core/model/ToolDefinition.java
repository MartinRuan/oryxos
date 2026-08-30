package com.oryxos.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 注入给大模型的工具契约描述符.
 *
 * @author oryxos
 */
public class ToolDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final String description;
  private final String inputJsonSchema;

  /**
   * 全参构造器.
   *
   * @param name 工具名称
   * @param description 工具功能说明
   * @param inputJsonSchema 参数 JSON Schema 字符串
   */
  public ToolDefinition(String name, String description, String inputJsonSchema) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.description = description != null ? description : "";
    this.inputJsonSchema = inputJsonSchema != null ? inputJsonSchema : "{}";
  }

  /**
   * 创建 Builder 构建器.
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getInputJsonSchema() {
    return inputJsonSchema;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolDefinition that = (ToolDefinition) o;
    return Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(inputJsonSchema, that.inputJsonSchema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description, inputJsonSchema);
  }

  @Override
  public String toString() {
    return "ToolDefinition{"
        + "name='"
        + name
        + '\''
        + ", description='"
        + description
        + '\''
        + ", inputJsonSchema='"
        + inputJsonSchema
        + '\''
        + '}';
  }

  /** Builder 构造器. */
  public static final class Builder {
    private String name;
    private String description;
    private String inputJsonSchema;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder inputJsonSchema(String inputJsonSchema) {
      this.inputJsonSchema = inputJsonSchema;
      return this;
    }

    /**
     * 构建 ToolDefinition 实例.
     *
     * @return ToolDefinition 对象
     */
    public ToolDefinition build() {
      return new ToolDefinition(name, description, inputJsonSchema);
    }
  }
}
