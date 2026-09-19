package com.oryxos.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 创建 Agent 请求，支持完整 AGENT.md 或结构化字段二选一.
 *
 * @author oryxos
 */
public record CreateAgentRequest(
    @NotBlank(message = "must not be blank") String name,
    String agentMarkdown,
    String description,
    String instructions,
    String provider,
    String model,
    Double temperature,
    List<String> tools,
    List<String> mcpServers) {

  /** 校验 raw 与 structured 模式互斥且字段完整. */
  @AssertTrue(
      message = "agentMarkdown and structured fields must be mutually exclusive and complete")
  public boolean isValidDefinition() {
    boolean raw = agentMarkdown != null && !agentMarkdown.isBlank();
    boolean structured = hasStructuredValue();
    return raw != structured && (raw || structuredComplete());
  }

  /** 将请求转换为完整 AGENT.md 文本. */
  public String toAgentMarkdown() {
    if (agentMarkdown != null && !agentMarkdown.isBlank()) {
      return agentMarkdown;
    }
    StringBuilder yaml = new StringBuilder("---\n");
    yaml.append("name: ").append(yaml(name)).append('\n');
    yaml.append("description: ").append(yaml(description)).append('\n');
    yaml.append("identity:\n  agent_name: ").append(yaml(name)).append('\n');
    yaml.append("  prompt: ").append(yaml(instructions)).append('\n');
    yaml.append("provider:\n  name: ").append(yaml(provider)).append('\n');
    yaml.append("  model: ").append(yaml(model)).append('\n');
    if (temperature != null) {
      yaml.append("  temperature: ").append(temperature).append('\n');
    }
    appendList(yaml, "tools", tools);
    appendList(yaml, "mcp_servers", mcpServers);
    yaml.append("settings:\n  max_iterations: 10\n  max_history_turns: 20\n---\n\n");
    yaml.append(instructions.trim()).append('\n');
    return yaml.toString();
  }

  private boolean hasStructuredValue() {
    return notBlank(description)
        || notBlank(instructions)
        || notBlank(provider)
        || notBlank(model)
        || temperature != null
        || (tools != null && !tools.isEmpty())
        || (mcpServers != null && !mcpServers.isEmpty());
  }

  private boolean structuredComplete() {
    return notBlank(description) && notBlank(instructions) && notBlank(provider) && notBlank(model);
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private String yaml(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  private void appendList(StringBuilder yaml, String key, List<String> values) {
    yaml.append(key).append(":\n");
    if (values != null) {
      values.forEach(value -> yaml.append("  - ").append(yaml(value)).append('\n'));
    }
  }
}
