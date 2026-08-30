package com.oryxos.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.session.JpaSessionManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * SessionRepository 与 JpaSessionManager 验收测试. 验证手工 schema.sql 建表后，sessions 表能正常存取、messages_json
 * 序列化回读消息完整且模拟重启历史不丢.
 *
 * @author OryxOS Team
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:sqlite::memory:",
      "spring.datasource.driver-class-name=org.sqlite.JDBC",
      "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.sql.init.mode=never"
    })
@Sql(scripts = "/schema.sql")
class SessionRepositoryTest {

  @SpringBootApplication
  @EntityScan("com.oryxos.storage.entity")
  @EnableJpaRepositories("com.oryxos.storage.repository")
  static class TestConfig {}

  @Autowired private SessionRepository repository;

  @Test
  @DisplayName("手工建表脚本建出的sessions表能存能读")
  void 手工建表脚本建出的sessions表能存能读() {
    String sessionId = "cli:user1:default";
    SessionEntity entity =
        new SessionEntity(
            sessionId,
            "default",
            "cli",
            "user1",
            "[]",
            "ACTIVE",
            LocalDateTime.now(),
            LocalDateTime.now(),
            null);
    repository.save(entity);

    Optional<SessionEntity> fetched = repository.findById(sessionId);
    assertThat(fetched).isPresent();
    assertThat(fetched.get().getSessionId()).isEqualTo(sessionId);
    assertThat(fetched.get().getProfileName()).isEqualTo("default");
    assertThat(fetched.get().getChannel()).isEqualTo("cli");
    assertThat(fetched.get().getUserId()).isEqualTo("user1");
    assertThat(fetched.get().getStatus()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("messages_json序列化回读后消息完整保真")
  void messages_json序列化回读后消息完整保真() {
    ObjectMapper mapper = new ObjectMapper();
    JpaSessionManager sessionManager = new JpaSessionManager(repository, mapper);

    Session session = sessionManager.getOrCreate("cli", "wang", "ops-agent");
    session.append(ChatMessage.user("查询系统负载"));
    session.append(
        ChatMessage.assistant(
            "正在调用工具", List.of(new ToolCallIntent("call-1", "shell", "{\"cmd\":\"uptime\"}"))));
    session.append(ChatMessage.tool("call-1", "load average: 0.15, 0.20, 0.18"));
    session.append(ChatMessage.assistant("当前系统负载正常"));

    sessionManager.save(session);

    // 重新通过 sessionManager 读取
    Optional<Session> reloaded = sessionManager.get(session.getId());
    assertTrue(reloaded.isPresent());
    Session loadedSession = reloaded.get();
    assertEquals(4, loadedSession.getMessages().size());

    ChatMessage m1 = loadedSession.getMessages().get(0);
    assertEquals("USER", m1.getRole().name());
    assertEquals("查询系统负载", m1.getContent());

    ChatMessage m2 = loadedSession.getMessages().get(1);
    assertEquals("ASSISTANT", m2.getRole().name());
    assertEquals("正在调用工具", m2.getContent());
    assertEquals(1, m2.getToolCalls().size());
    assertEquals("shell", m2.getToolCalls().get(0).getName());

    ChatMessage m3 = loadedSession.getMessages().get(2);
    assertEquals("TOOL", m3.getRole().name());
    assertEquals("call-1", m3.getToolCallId());

    ChatMessage m4 = loadedSession.getMessages().get(3);
    assertEquals("ASSISTANT", m4.getRole().name());
    assertEquals("当前系统负载正常", m4.getContent());
  }

  @Test
  @DisplayName("模拟重启新建context重查历史还在")
  void 模拟重启新建context重查历史还在() {
    ObjectMapper mapper = new ObjectMapper();
    JpaSessionManager managerInstance1 = new JpaSessionManager(repository, mapper);

    Session session = managerInstance1.getOrCreate("cli", "admin", "default");
    session.append(ChatMessage.user("第一轮提问"));
    session.append(ChatMessage.assistant("第一轮答复"));
    managerInstance1.save(session);

    // 模拟应用重启：新建另一个 JpaSessionManager 实例查询同一个三元组
    JpaSessionManager managerInstance2 = new JpaSessionManager(repository, mapper);
    Session recovered = managerInstance2.getOrCreate("cli", "admin", "default");

    assertNotNull(recovered);
    assertEquals(session.getId(), recovered.getId(), "重启后获取到的 sessionId 必须保持一致");
    assertEquals(2, recovered.getMessages().size(), "重启后历史消息必须完好保留");
  }
}
