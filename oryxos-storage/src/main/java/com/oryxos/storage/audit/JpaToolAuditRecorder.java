package com.oryxos.storage.audit;

import com.oryxos.core.tool.ToolAuditRecorder;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA / SQLite 的工具调用审计记录器.
 *
 * @author oryxos
 */
@Component
public class JpaToolAuditRecorder implements ToolAuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(JpaToolAuditRecorder.class);

  private final ToolInvocationRepository repository;

  /**
   * 构造函数注入仓储.
   *
   * @param repository 工具调用仓储
   */
  public JpaToolAuditRecorder(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      ToolInvocationEntity entity =
          new ToolInvocationEntity(
              UUID.randomUUID().toString(),
              sessionId,
              toolName,
              inputJson,
              resultJson,
              success,
              errorMessage,
              durationMs,
              Instant.now());
      repository.save(entity);
    } catch (Exception e) {
      log.warn("Failed to persist tool invocation audit record", e);
    }
  }
}
