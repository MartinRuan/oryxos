package com.oryxos.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

/**
 * 更新 Agent 请求；路径名称是最终 Agent 名称.
 *
 * @author oryxos
 */
public record UpdateAgentRequest(
    String agentMarkdown,
    String description,
    String instructions,
    String provider,
    String model,
    Double temperature,
    List<String> tools,
    List<String> mcpServers) {

  @AssertTrue(
      message = "agentMarkdown and structured fields must be mutually exclusive and complete")
  public boolean isValidDefinition() {
    return asCreate("placeholder").isValidDefinition();
  }

  public String toAgentMarkdown(String name) {
    return asCreate(name).toAgentMarkdown();
  }

  private @Valid CreateAgentRequest asCreate(String name) {
    return new CreateAgentRequest(
        name,
        agentMarkdown,
        description,
        instructions,
        provider,
        model,
        temperature,
        tools,
        mcpServers);
  }
}
