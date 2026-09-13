package com.oryxos.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.scheduler.ScheduledTaskStore.TaskState;
import com.oryxos.storage.scheduler.JpaScheduledTaskStore;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

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
class ScheduledTaskStoreTest {

  @SpringBootApplication
  @EntityScan("com.oryxos.storage.entity")
  @EnableJpaRepositories("com.oryxos.storage.repository")
  static class TestConfig {}

  @Autowired private ScheduledTaskRepository taskRepository;
  @Autowired private TaskExecutionRepository executionRepository;

  @Test
  void reconcileKeepsStableIdAndEnabledStateAndPersistsExecution() {
    JpaScheduledTaskStore store = new JpaScheduledTaskStore(taskRepository, executionRepository);
    TaskState created =
        store.reconcile(
            "ops", "daily-report", "Daily report", "0 0 9 * * *", "Asia/Shanghai", "send");
    store.setEnabled(created.scheduleId(), false);

    TaskState reconciled =
        store.reconcile(
            "ops", "daily-report", "Daily report", "0 30 9 * * *", "Asia/Shanghai", "send");
    assertThat(reconciled.scheduleId()).isEqualTo(created.scheduleId());
    assertThat(reconciled.enabled()).isFalse();
    assertThat(reconciled.cron()).isEqualTo("0 30 9 * * *");

    Instant startedAt = Instant.now();
    store.recordExecution(
        created.scheduleId(), "scheduler:scheduler:ops", startedAt, true, null, 25);
    assertThat(store.executions(created.scheduleId())).hasSize(1);
    assertThat(store.find(created.scheduleId()).orElseThrow().runCount()).isEqualTo(1);
    assertThat(store.find(created.scheduleId()).orElseThrow().lastStatus()).isEqualTo("success");

    store.retire("ops", Set.of());
    assertThat(store.find(created.scheduleId())).isEmpty();
    assertThat(store.list()).isEmpty();
    assertThat(store.executions(created.scheduleId())).hasSize(1);
  }
}
