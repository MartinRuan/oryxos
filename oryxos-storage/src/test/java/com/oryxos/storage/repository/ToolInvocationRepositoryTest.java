package com.oryxos.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.storage.entity.ToolInvocationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * ToolInvocationRepository 验收测试. 验证手工 schema.sql 建表后，tool_invocations 实体能正常存取，success 与
 * error_message 字段真实有效.
 *
 * @author oryxos
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
class ToolInvocationRepositoryTest {

  @SpringBootApplication
  @EntityScan("com.oryxos.storage.entity")
  @EnableJpaRepositories("com.oryxos.storage.repository")
  static class TestConfig {}

  @Autowired private ToolInvocationRepository repository;

  @Test
  @DisplayName("验证手工建表脚本建出的 tool_invocations 能存能读，success与error_message两列真实存在且映射准确")
  void 手工建表脚本建出的tool_invocations_能存能读且字段完整() {
    String sessionId = "session-17-test";
    String callIdSuccess = UUID.randomUUID().toString();

    // 1. 记录成功工具调用
    ToolInvocationEntity successCall =
        new ToolInvocationEntity(
            callIdSuccess,
            sessionId,
            "read_file",
            "{\"path\":\"/tmp/app.log\"}",
            "{\"content\":\"2026-08-30 OK\"}",
            true,
            null,
            45L,
            Instant.now());
    repository.save(successCall);

    // 2. 记录失败工具调用
    String callIdFail = UUID.randomUUID().toString();
    ToolInvocationEntity failCall =
        new ToolInvocationEntity(
            callIdFail,
            sessionId,
            "shell",
            "{\"command\":\"rm -rf /\"}",
            null,
            false,
            "Sandbox violation: command not in whitelist",
            12L,
            Instant.now());
    repository.save(failCall);

    // 3. 校验查询与字段映射
    Optional<ToolInvocationEntity> fetchedSuccess = repository.findById(callIdSuccess);
    assertThat(fetchedSuccess).isPresent();
    assertThat(fetchedSuccess.get().getToolName()).isEqualTo("read_file");
    assertThat(fetchedSuccess.get().getInputJson()).isEqualTo("{\"path\":\"/tmp/app.log\"}");
    assertThat(fetchedSuccess.get().getResultJson()).isEqualTo("{\"content\":\"2026-08-30 OK\"}");
    assertThat(fetchedSuccess.get().isSuccess()).isTrue();
    assertThat(fetchedSuccess.get().getErrorMessage()).isNull();
    assertThat(fetchedSuccess.get().getDurationMs()).isEqualTo(45L);

    Optional<ToolInvocationEntity> fetchedFail = repository.findById(callIdFail);
    assertThat(fetchedFail).isPresent();
    assertThat(fetchedFail.get().getToolName()).isEqualTo("shell");
    assertThat(fetchedFail.get().isSuccess()).isFalse();
    assertThat(fetchedFail.get().getErrorMessage())
        .isEqualTo("Sandbox violation: command not in whitelist");
    assertThat(fetchedFail.get().getDurationMs()).isEqualTo(12L);

    // 4. 按会话查询
    List<ToolInvocationEntity> sessionInvocations = repository.findBySessionId(sessionId);
    assertThat(sessionInvocations).hasSize(2);
  }
}
