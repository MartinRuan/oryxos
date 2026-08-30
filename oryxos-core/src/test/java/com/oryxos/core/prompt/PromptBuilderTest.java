package com.oryxos.core.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.oryxos.core.context.ContextLoader;
import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.ChatRequest;
import com.oryxos.core.model.MessageType;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.prompt.impl.PromptBuilderImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

  @Mock private ContextLoader contextLoader;

  private PromptBuilder promptBuilder;
  private Profile profile;
  private Session session;

  @BeforeEach
  void setUp() {
    promptBuilder = new PromptBuilderImpl(contextLoader);

    profile =
        Profile.builder()
            .name("ops-agent")
            .identity(new Profile.Identity("运维小欧", "你是一个专业的运维助手。"))
            .tools(List.of("read_file", "shell"))
            .settings(new Profile.Settings(10, 20))
            .build();

    session = new Session("session-test-01", "ops-agent", "cli", "user-01");
  }

  @Test
  @DisplayName("四大组成部分顺序正确拼装")
  void 四部分顺序正确() {
    when(contextLoader.loadContext(profile)).thenReturn("Bootstrap: [AGENTS.md, USER.md]");

    session.append("检查服务器磁盘空间");
    session.append(ChatMessage.assistant("正在读取 /dev/sda1 容量"));

    ChatRequest request = promptBuilder.build(session, profile);

    List<ChatMessage> messages = request.getMessages();
    assertEquals(3, messages.size());

    // 第一条必定为 SYSTEM 提示词
    assertEquals(MessageType.SYSTEM, messages.get(0).getRole());
    assertTrue(messages.get(0).getContent().contains("你是一个专业的运维助手。"));
    assertTrue(messages.get(0).getContent().contains("Bootstrap: [AGENTS.md, USER.md]"));
    assertTrue(messages.get(0).getContent().contains("当前日期时间: "));

    // 接下来为对话历史
    assertEquals(MessageType.USER, messages.get(1).getRole());
    assertEquals("检查服务器磁盘空间", messages.get(1).getContent());

    assertEquals(MessageType.ASSISTANT, messages.get(2).getRole());
    assertEquals("正在读取 /dev/sda1 容量", messages.get(2).getContent());

    // 工具列表
    assertEquals(2, request.getTools().size());
    assertEquals("read_file", request.getTools().get(0).getName());
    assertEquals("shell", request.getTools().get(1).getName());
  }

  @Test
  @DisplayName("历史消息超过 20 轮被准确截断")
  void 历史超20轮被截断() {
    // 写入 30 条对话消息 (15 轮问答)
    for (int i = 1; i <= 30; i++) {
      if (i % 2 == 1) {
        session.append(ChatMessage.user("用户问题 " + i));
      } else {
        session.append(ChatMessage.assistant("助手回答 " + i));
      }
    }

    ChatRequest request = promptBuilder.build(session, profile);

    // 1 个 System 消息 + 20 条截断历史 = 21 条消息
    assertEquals(21, request.getMessages().size());
    assertEquals(MessageType.SYSTEM, request.getMessages().get(0).getRole());

    // 最早保留的消息应为第 11 条 ("用户问题 11")
    assertEquals("用户问题 11", request.getMessages().get(1).getContent());
    assertEquals("助手回答 30", request.getMessages().get(20).getContent());
  }

  @Test
  @DisplayName("System Prompt 末尾必须附加当前日期时间")
  void system_prompt末尾含当前日期时间() {
    ChatRequest request = promptBuilder.build(session, profile);

    assertFalse(request.getMessages().isEmpty());
    ChatMessage systemMsg = request.getMessages().get(0);
    assertEquals(MessageType.SYSTEM, systemMsg.getRole());
    assertTrue(systemMsg.getContent().contains("当前日期时间: "));
  }
}
