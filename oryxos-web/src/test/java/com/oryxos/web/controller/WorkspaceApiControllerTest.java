package com.oryxos.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.profile.AgentStore;
import com.oryxos.web.exception.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceApiControllerTest {

  @TempDir Path tempDir;
  private AgentStore store;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    store = new AgentStore(tempDir.resolve(".oryxos"));
    mockMvc =
        MockMvcBuilders.standaloneSetup(new WorkspaceApiController(store))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void 树稳定返回agents和archive且文件可读取() throws Exception {
    store.create("ops", "hello");

    mockMvc
        .perform(get("/api/v1/workspace/tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("agents"))
        .andExpect(jsonPath("$.data[1].name").value("archive"));
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents/ops/AGENT.md"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("hello"));
  }

  @Test
  void 缺失为404且目录绝对路径和越界为400() throws Exception {
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents/no.md"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "/etc/passwd"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "../../etc/passwd"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 工作区内软链接指向外部时拒绝读取() throws Exception {
    Path outside = tempDir.resolve("secret.txt");
    Files.writeString(outside, "secret");
    Path link = store.agentsRoot().resolve("escape.txt");
    Files.createSymbolicLink(link, outside);

    mockMvc
        .perform(get("/api/v1/workspace/file").param("path", "agents/escape.txt"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }
}
