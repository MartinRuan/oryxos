package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.ChatResponse;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.model.ToolResult;
import com.oryxos.core.prompt.PromptBuilder;
import com.oryxos.core.react.impl.ReActLoopImpl;
import com.oryxos.core.tool.ToolExecutor;
import com.oryxos.provider.ProviderService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReActLoopTest {

  @Mock private ProviderService providerService;
  @Mock private PromptBuilder promptBuilder;
  @Mock private ToolExecutor toolExecutor;

  private ReActLoop reActLoop;
  private Session session;
  private Profile profile;

  @BeforeEach
  void setUp() {
    reActLoop = new ReActLoopImpl(providerService, promptBuilder, toolExecutor);
    session = new Session("test-session-123", "ops-agent", "cli", "user-001");
    profile = Profile.builder().name("ops-agent").settings(new Profile.Settings(10, 20)).build();

    when(promptBuilder.build(any(), any()))
        .thenReturn(ChatRequest.builder().prompt("test prompt").build());
  }

  @Test
  @DisplayName("模型无工具调用时一轮收尾")
  void 模型无工具调用_一轮收尾() {
    when(providerService.chat(eq("test-session-123"), eq(profile), any(ChatRequest.class)))
        .thenReturn(ChatResponse.of("你好，我是运维助手。"));

    String reply = reActLoop.run(session, "你好", profile);

    assertEquals("你好，我是运维助手。", reply);
    verify(providerService, times(1)).chat(any(), any(), any(ChatRequest.class));
    assertEquals(2, session.getMessages().size());
  }

  @Test
  @DisplayName("有工具调用则执行并回填进下一轮")
  void 有工具调用_执行并回填进下一轮() {
    ToolCallIntent weatherCall =
        new ToolCallIntent("call-weather-1", "http_get", "{\"url\":\"https://api.weather.com\"}");
    ChatResponse firstResp = ChatResponse.builder().toolCalls(List.of(weatherCall)).build();
    ChatResponse secondResp = ChatResponse.of("今天北京气温 25℃，晴。");

    when(providerService.chat(eq("test-session-123"), eq(profile), any(ChatRequest.class)))
        .thenReturn(firstResp)
        .thenReturn(secondResp);

    when(toolExecutor.execute("test-session-123", weatherCall))
        .thenReturn(ToolResult.success("{\"temp\":25,\"condition\":\"Sunny\"}"));

    String reply = reActLoop.run(session, "查询北京天气", profile);

    assertEquals("今天北京气温 25℃，晴。", reply);
    verify(providerService, times(2)).chat(any(), any(), any(ChatRequest.class));
    verify(toolExecutor, times(1)).execute("test-session-123", weatherCall);

    // user + assistant(tool_call) + tool_result + assistant(final) = 4
    assertEquals(4, session.getMessages().size());
  }

  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void 模型一直要调工具_转满最大轮数强制停() {
    ToolCallIntent infiniteCall =
        new ToolCallIntent("call-infinite", "http_get", "{\"url\":\"https://example.com\"}");
    ChatResponse callResp = ChatResponse.builder().toolCalls(List.of(infiniteCall)).build();

    when(providerService.chat(any(), any(), any(ChatRequest.class))).thenReturn(callResp);
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("{\"result\":\"ok\"}"));

    String reply = reActLoop.run(session, "查天气", profile);

    verify(providerService, times(10)).chat(any(), any(), any(ChatRequest.class));
    assertTrue(reply.contains("达到最大轮数"));
  }

  @Test
  @DisplayName("每轮响应和工具结果都累积进Session")
  void 每轮响应和工具结果都累积进Session() {
    ToolCallIntent callIntent =
        new ToolCallIntent("call-test", "read_file", "{\"path\":\"app.log\"}");
    ChatResponse firstResp = ChatResponse.builder().toolCalls(List.of(callIntent)).build();
    ChatResponse finalResp = ChatResponse.of("日志读取完毕，无异常。");

    when(providerService.chat(any(), any(), any(ChatRequest.class)))
        .thenReturn(firstResp)
        .thenReturn(finalResp);
    when(toolExecutor.execute(any(), any())).thenReturn(ToolResult.success("log content here"));

    reActLoop.run(session, "检查日志", profile);

    assertEquals(4, session.getMessages().size());
    assertEquals("检查日志", session.getMessages().get(0).getContent());
    assertEquals(1, session.getMessages().get(1).getToolCalls().size());
    assertEquals("log content here", session.getMessages().get(2).getContent());
    assertEquals("日志读取完毕，无异常。", session.getMessages().get(3).getContent());
  }
}
