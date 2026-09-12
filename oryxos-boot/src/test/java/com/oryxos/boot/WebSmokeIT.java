package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
  void 真实上下文装配六个只读端点与Jpa仓储() throws Exception {
    for (String path : List.of("health", "info", "profiles", "tools", "memory", "sessions")) {
      mockMvc
          .perform(get("/api/v1/" + path))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(0));
    }
  }

  @Test
  void 六个Controller准确暴露11个操作并生成OpenAPI() throws Exception {
    List<Map.Entry<RequestMappingInfo, HandlerMethod>> webApiMappings =
        handlerMapping.getHandlerMethods().entrySet().stream().filter(this::isWebApi).toList();
    long operationCount =
        webApiMappings.stream()
            .mapToLong(entry -> entry.getKey().getMethodsCondition().getMethods().size())
            .sum();
    long controllerCount =
        webApiMappings.stream().map(entry -> entry.getValue().getBeanType()).distinct().count();

    assertThat(operationCount).isEqualTo(11);
    assertThat(controllerCount).isEqualTo(6);
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/sessions")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("/api/v1/agents/{name}/invoke")));
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
  void 管理台包含五个只读区域并只引用GET数据源() throws Exception {
    mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    mockMvc
        .perform(get("/admin/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Sessions")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Profiles")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Tools")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Memory")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Runtime Status")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<form"))));

    mockMvc
        .perform(get("/admin/app.js"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/sessions")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/profiles")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/tools")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/memory")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/info")))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("method:"))));
  }
}
