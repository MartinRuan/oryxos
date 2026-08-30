package com.oryxos.core.session;

import com.oryxos.core.model.Session;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存态 SessionManager 缺省实现.
 *
 * <p>为单机/离线运行提供开箱即用的会话管理支持.
 *
 * @author oryxos
 */
public class InMemorySessionManager implements SessionManager {

  private final Map<String, Session> sessionStore = new ConcurrentHashMap<>();

  @Override
  public Session getOrCreate(String sessionId, String profileName, String channel, String userId) {
    String sid =
        (sessionId != null && !sessionId.isBlank())
            ? sessionId.trim()
            : UUID.randomUUID().toString();
    return sessionStore.computeIfAbsent(sid, id -> new Session(id, profileName, channel, userId));
  }

  @Override
  public Optional<Session> get(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(sessionStore.get(sessionId));
  }

  @Override
  public void save(Session session) {
    if (session != null && session.getId() != null) {
      sessionStore.put(session.getId(), session);
    }
  }

  @Override
  public void archive(String sessionId) {
    if (sessionId != null) {
      sessionStore.remove(sessionId);
    }
  }
}
