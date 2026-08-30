package com.oryxos.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.storage.entity.LlmCallEntity;
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
 * LlmCallRepository 验收测试. 验证手工 schema.sql 建表后，llm_calls 实体能正常存取，success 与 error_message 字段真实有效.
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
class LlmCallRepositoryTest {

  @SpringBootApplication
  @EntityScan("com.oryxos.storage.entity")
  @EnableJpaRepositories("com.oryxos.storage.repository")
  static class TestConfig {}

  @Autowired private LlmCallRepository repository;

  @Test
  @DisplayName("验证手工建表脚本建出的 llm_calls 能存能读，success与error_message两列真实存在且映射准确")
  void 手工建表脚本建出的llm_calls_能存能读且字段完整() {
    String sessionId = "sess-16-test";
    String callIdSuccess = UUID.randomUUID().toString();

    // 1. 记录成功调用
    LlmCallEntity successCall =
        new LlmCallEntity(
            callIdSuccess,
            sessionId,
            "deepseek",
            "deepseek-chat",
            100,
            50,
            150,
            850L,
            true,
            null,
            Instant.now());
    repository.save(successCall);

    // 2. 记录失败调用（超时事故）
    String callIdFail = UUID.randomUUID().toString();
    LlmCallEntity failCall =
        new LlmCallEntity(
            callIdFail,
            sessionId,
            "qwen",
            "qwen-plus",
            0,
            0,
            0,
            3000L,
            false,
            "Connection timed out to Qwen API",
            Instant.now());
    repository.save(failCall);

    // 3. 验证通过 ID 查询
    Optional<LlmCallEntity> fetchedSuccess = repository.findById(callIdSuccess);
    assertThat(fetchedSuccess).isPresent();
    assertThat(fetchedSuccess.get().getProvider()).isEqualTo("deepseek");
    assertThat(fetchedSuccess.get().getModel()).isEqualTo("deepseek-chat");
    assertThat(fetchedSuccess.get().getPromptTokens()).isEqualTo(100);
    assertThat(fetchedSuccess.get().getCompletionTokens()).isEqualTo(50);
    assertThat(fetchedSuccess.get().getTotalTokens()).isEqualTo(150);
    assertThat(fetchedSuccess.get().getDurationMs()).isEqualTo(850L);
    assertThat(fetchedSuccess.get().isSuccess()).isTrue();
    assertThat(fetchedSuccess.get().getErrorMessage()).isNull();

    Optional<LlmCallEntity> fetchedFail = repository.findById(callIdFail);
    assertThat(fetchedFail).isPresent();
    assertThat(fetchedFail.get().getProvider()).isEqualTo("qwen");
    assertThat(fetchedFail.get().isSuccess()).isFalse();
    assertThat(fetchedFail.get().getErrorMessage()).contains("Connection timed out");
    assertThat(fetchedFail.get().getDurationMs()).isEqualTo(3000L);

    // 4. 按 session 查询
    List<LlmCallEntity> sessionCalls = repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    assertThat(sessionCalls).hasSize(2);
  }
}
