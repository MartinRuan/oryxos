package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Tag("integration")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:sqlite:/tmp/oryxos-web-smoke.db",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.sql.init.mode=always"
    })
@AutoConfigureMockMvc
class WebSmokeIT {

  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void 真实上下文装配管理与只读端点以及Jpa仓储() throws Exception {
    for (String path :
        List.of("health", "info", "profiles", "tools", "memory", "sessions", "agents")) {
      mockMvc
          .perform(get("/api/v1/" + path))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(0));
    }
    mockMvc
        .perform(get("/api/v1/workspace/tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
  }

  @Test
  void 七个Controller准确暴露19个操作并生成OpenAPI() throws Exception {
    List<Map.Entry<RequestMappingInfo, HandlerMethod>> webApiMappings =
        handlerMapping.getHandlerMethods().entrySet().stream().filter(this::isWebApi).toList();
    long operationCount =
        webApiMappings.stream()
            .mapToLong(entry -> entry.getKey().getMethodsCondition().getMethods().size())
            .sum();
    long controllerCount =
        webApiMappings.stream().map(entry -> entry.getValue().getBeanType()).distinct().count();

    assertThat(operationCount).isEqualTo(19);
    assertThat(controllerCount).isEqualTo(7);
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/sessions")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("/api/v1/agents/{name}/invoke")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("/api/v1/agents/generate")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("/api/v1/workspace/tree")));
    mockMvc.perform(get("/swagger-ui")).andExpect(status().is3xxRedirection());
  }

  private boolean isWebApi(Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
    boolean webController =
        entry.getValue().getBeanType().getPackageName().startsWith("com.oryxos.web.controller");
    boolean apiPath =
        entry.getKey().getPatternValues().stream().anyMatch(path -> path.startsWith("/api/v1"));
    return webController && apiPath;
  }

  @Test
  void 管理台使用中文四项导航并包含Agent和工作区能力() throws Exception {
    mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    byte[] htmlBytes =
        mockMvc
            .perform(get("/admin/index.html"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String html = new String(htmlBytes, StandardCharsets.UTF_8);

    assertThat(html)
        .contains(
            "lang=\"zh-CN\"",
            "使用 MiniMax 生成草稿",
            "工作区",
            "agent-form",
            "new-agent",
            "agent-table",
            "<th>操作</th>",
            "agent-detail-view",
            "back-agent-list",
            "返回 Agent 列表",
            "data-agent-tab=",
            "agent-basic-panel",
            "agent-files-panel",
            "agent-sessions-panel",
            "agent-memory-panel",
            "agent-memory-content",
            "基本信息",
            "运行看板",
            "Agent 数量",
            "内置工具",
            "活跃会话",
            "Provider",
            "核心能力",
            "技术栈",
            "运行状态")
        .containsSubsequence(
            "data-target=\"overview\" class=\"active\">概览</a>",
            "data-target=\"agents\">Agent列表</a>",
            "data-target=\"schedules\">定时任务</a>",
            "data-target=\"runtime\" aria-expanded=\"true\">OS运行时</a>")
        .containsSubsequence(
            "aria-label=\"OS运行时子菜单\"",
            "data-section=\"runtime-providers\">Provider（模型提供商）</a>",
            "data-section=\"runtime-tools\">Tool（工具）</a>",
            "data-section=\"runtime-sandbox\">Sandbox白名单</a>")
        .containsSubsequence("Provider（模型提供商）", "Tool（工具）", "Sandbox白名单")
        .doesNotContain(
            "data-target=\"workspace\"",
            "data-target=\"tools\"",
            "data-target=\"memory\"",
            "runtime-system",
            "系统信息",
            "data-section=\"runtime-sessions\"",
            "data-section=\"runtime-memory\"");

    byte[] scriptBytes =
        mockMvc
            .perform(get("/admin/app.js"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String script = new String(scriptBytes, StandardCharsets.UTF_8);
    assertThat(script)
        .contains(
            "/api/v1/sessions",
            "/api/v1/agents",
            "/api/v1/workspace",
            "/api/v1/tools",
            "/api/v1/memory",
            "/api/v1/info",
            "method: \"POST\"",
            "method: \"DELETE\"",
            "agent-memory-content",
            "?agent=${encodeURIComponent(item.name)}",
            "renderAgentMemory",
            "builtinToolNames",
            "setDashboardMetric",
            "renderAgentList",
            "showAgentDetails",
            "selectAgentTab",
            "showAgentList",
            "findWorkspaceNode",
            "session.profileName === agentName",
            "agent-file-preview",
            "\"详情\"",
            "\"删除\"",
            "selectRuntimeView",
            "runtimeGroup.classList.toggle(\"collapsed\")",
            "runtimeToggle.setAttribute(\"aria-expanded\"",
            "runtime-providers",
            "草稿已生成，请确认内容后再保存。")
        .doesNotContain("scrollIntoView");
  }
}
