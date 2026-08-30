package com.oryxos.channel.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Session;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.InMemorySessionManager;
import com.oryxos.core.session.SessionManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CliChannel 控制台交互渠道验收测试.
 *
 * @author OryxOS Team
 */
@ExtendWith(MockitoExtension.class)
class CliChannelTest {

  @Mock private AgentService agentService;

  private SessionManager sessionManager;
  private CliChannel cliChannel;

  @BeforeEach
  void setUp() {
    sessionManager = new InMemorySessionManager();
    cliChannel = new CliChannel(agentService, sessionManager);
  }

  @Test
  @DisplayName("交互式对话循环读输入并在遇到 /quit 时退出")
  void 交互式对话循环读输入并在遇到quit时退出() {
    String simulatedInput = "你好\n今天天气如何\n/quit\n";
    InputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outContent, true, StandardCharsets.UTF_8);

    when(agentService.process(any(Session.class), eq("你好"))).thenReturn("你好！我是 OryxOS 智能助手。");
    when(agentService.process(any(Session.class), eq("今天天气如何"))).thenReturn("今天晴空万里，温度 22 度。");

    cliChannel.run("weather-agent", in, out);

    verify(agentService, times(1)).process(any(Session.class), eq("你好"));
    verify(agentService, times(1)).process(any(Session.class), eq("今天天气如何"));

    String outputText = outContent.toString(StandardCharsets.UTF_8);
    assertTrue(outputText.contains("你好！我是 OryxOS 智能助手。"));
    assertTrue(outputText.contains("今天晴空万里，温度 22 度。"));
  }

  @Test
  @DisplayName("空行输入不触发模型处理")
  void 空行输入不触发模型处理() {
    String simulatedInput = "   \n\n/quit\n";
    InputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outContent, true, StandardCharsets.UTF_8);

    cliChannel.run("default", in, out);

    verify(agentService, times(0)).process(any(Session.class), any());
  }
}
