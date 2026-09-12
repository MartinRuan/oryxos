package com.oryxos.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.Session;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 会话管理器核心契约验收测试.
 *
 * @author OryxOS Team
 */
class SessionManagerTest {

  private SessionManager sessionManager;

  @BeforeEach
  void setUp() {
    sessionManager = new InMemorySessionManager();
  }

  @Test
  @DisplayName("同一三元组_历次getOrCreate都是同一个Session")
  void 同一三元组_历次getOrCreate都是同一个Session() {
    Session first = sessionManager.getOrCreate("cli", "wang", "default");
    Session second = sessionManager.getOrCreate("cli", "wang", "default");

    assertNotNull(first);
    assertNotNull(second);
    assertEquals(first.getId(), second.getId(), "幂等：同一三元组多次获取必须是同一个 Session");
    assertEquals("cli", first.getChannel());
    assertEquals("wang", first.getUserId());
    assertEquals("default", first.getProfileName());

    Session other = sessionManager.getOrCreate("web", "wang", "default");
    assertNotEquals(first.getId(), other.getId(), "channel 不同必须是不同会话");
  }

  @Test
  @DisplayName("channel或user或profile任一不同_为不同Session")
  void channel或user或profile任一不同_为不同Session() {
    Session s1 = sessionManager.getOrCreate("cli", "user1", "agent1");
    Session s2 = sessionManager.getOrCreate("cli", "user2", "agent1");
    Session s3 = sessionManager.getOrCreate("cli", "user1", "agent2");
    Session s4 = sessionManager.getOrCreate("web", "user1", "agent1");

    assertNotEquals(s1.getId(), s2.getId(), "user 不同隔离会话");
    assertNotEquals(s1.getId(), s3.getId(), "profile 不同隔离会话");
    assertNotEquals(s1.getId(), s4.getId(), "channel 不同隔离会话");
  }

  @Test
  @DisplayName("id生成规则在SessionManager内部收敛")
  void id生成规则在SessionManager内部收敛() {
    String expectedId = SessionManager.generateSessionId("cli", "admin", "ops");
    Session session = sessionManager.getOrCreate("cli", "admin", "ops");

    assertEquals(expectedId, session.getId(), "sessionId 必须与内部标准生成规则严格一致");
    assertEquals("cli:admin:ops", session.getId());
  }

  @Test
  @DisplayName("save与get能正确维护消息与状态")
  void save与get能正确维护消息与状态() {
    Session session = sessionManager.getOrCreate("cli", "dev", "default");
    session.append(ChatMessage.user("hello"));
    session.append(ChatMessage.assistant("hi there"));
    sessionManager.save(session);

    Optional<Session> retrieved = sessionManager.get(session.getId());
    assertTrue(retrieved.isPresent());
    assertEquals(2, retrieved.get().getMessages().size());
  }

  @Test
  @DisplayName("archive归档会话")
  void archive归档会话() {
    Session session = sessionManager.getOrCreate("cli", "tester", "default");
    sessionManager.archive(session.getId());

    Optional<Session> retrieved = sessionManager.get(session.getId());
    assertTrue(retrieved.isPresent());
    assertEquals("ARCHIVED", retrieved.get().getStatus());
    assertNotNull(retrieved.get().getArchivedAt());
  }

  @Test
  @DisplayName("空存储返回空会话列表")
  void 空存储返回空会话列表() {
    assertTrue(sessionManager.list().isEmpty());
  }

  @Test
  @DisplayName("会话列表按最后活动时间倒序且包含归档会话")
  void 会话列表按最后活动时间倒序且包含归档会话() {
    Session older = sessionManager.getOrCreate("web", "older", "default");
    older.setLastActiveAt(LocalDateTime.of(2026, 9, 12, 10, 0));
    sessionManager.save(older);
    Session newer = sessionManager.getOrCreate("web", "newer", "default");
    newer.setLastActiveAt(LocalDateTime.of(2026, 9, 12, 11, 0));
    sessionManager.save(newer);
    sessionManager.archive(older.getId());

    List<Session> sessions = sessionManager.list();

    assertEquals(
        List.of(newer.getId(), older.getId()), sessions.stream().map(Session::getId).toList());
    assertEquals("ARCHIVED", sessions.get(1).getStatus());
  }
}
