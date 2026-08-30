package com.oryxos.storage.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.MessageType;
import com.oryxos.core.model.Session;
import com.oryxos.core.model.ToolCallIntent;
import com.oryxos.core.session.SessionManager;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JPA / SQLite 的持久化 SessionManager 实现.
 *
 * <p>保证会话在多轮交互、服务重启与各入口间幂等复用与历史还原.
 *
 * @author OryxOS Team
 */
@Service
public class JpaSessionManager implements SessionManager {

  private static final Logger log = LoggerFactory.getLogger(JpaSessionManager.class);
  private static final String EMPTY_ARRAY_JSON = "[]";
  private static final TypeReference<List<ChatMessageDto>> MESSAGE_LIST_TYPE_REF =
      new TypeReference<>() {};

  private final SessionRepository sessionRepository;
  private final ObjectMapper objectMapper;

  /**
   * 构造函数.
   *
   * @param sessionRepository 会话 JPA 仓储
   * @param objectMapper Jackson JSON 序列化器
   */
  public JpaSessionManager(SessionRepository sessionRepository, ObjectMapper objectMapper) {
    this.sessionRepository =
        Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Session getOrCreate(String channel, String user, String profileName) {
    String sessionId = SessionManager.generateSessionId(channel, user, profileName);
    return getOrCreate(sessionId, profileName, channel, user);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Session getOrCreate(String sessionId, String profileName, String channel, String userId) {
    String effectiveSessionId =
        (sessionId != null && !sessionId.isBlank())
            ? sessionId.trim()
            : SessionManager.generateSessionId(channel, userId, profileName);

    Optional<SessionEntity> existing = sessionRepository.findById(effectiveSessionId);
    if (existing.isPresent()) {
      return toDomain(existing.get());
    }

    Session session = new Session(effectiveSessionId, profileName, channel, userId);
    SessionEntity entity = toEntity(session);
    sessionRepository.save(entity);
    log.debug("Created new session in SQLite: {}", effectiveSessionId);
    return session;
  }

  @Override
  @Transactional(readOnly = true, rollbackFor = Exception.class)
  public Optional<Session> get(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    return sessionRepository.findById(sessionId.trim()).map(this::toDomain);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void save(Session session) {
    if (session == null || session.getId() == null) {
      log.warn("Attempted to save null or invalid session");
      return;
    }
    session.setLastActiveAt(LocalDateTime.now());
    SessionEntity entity = toEntity(session);
    sessionRepository.save(entity);
    log.debug("Saved session to SQLite: {}", session.getId());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void archive(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }
    sessionRepository
        .findById(sessionId.trim())
        .ifPresent(
            entity -> {
              entity.setStatus("ARCHIVED");
              entity.setArchivedAt(LocalDateTime.now());
              sessionRepository.save(entity);
              log.info("Archived session: {}", sessionId);
            });
  }

  private SessionEntity toEntity(Session domain) {
    String messagesJson = serializeMessages(domain.getMessages());
    return new SessionEntity(
        domain.getId(),
        domain.getProfileName(),
        domain.getChannel(),
        domain.getUserId(),
        messagesJson,
        domain.getStatus(),
        domain.getCreatedAt(),
        domain.getLastActiveAt(),
        domain.getArchivedAt());
  }

  private Session toDomain(SessionEntity entity) {
    Session domain =
        new Session(
            entity.getSessionId(),
            entity.getProfileName(),
            entity.getChannel(),
            entity.getUserId());
    domain.setStatus(entity.getStatus());
    domain.setCreatedAt(entity.getCreatedAt());
    domain.setLastActiveAt(entity.getLastActiveAt());
    domain.setArchivedAt(entity.getArchivedAt());
    domain.setMessages(deserializeMessages(entity.getMessagesJson()));
    return domain;
  }

  private String serializeMessages(List<ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return EMPTY_ARRAY_JSON;
    }
    try {
      List<ChatMessageDto> dtos = messages.stream().map(this::toDto).collect(Collectors.toList());
      return objectMapper.writeValueAsString(dtos);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize session messages to JSON", e);
      return EMPTY_ARRAY_JSON;
    }
  }

  private List<ChatMessage> deserializeMessages(String messagesJson) {
    if (messagesJson == null
        || messagesJson.isBlank()
        || EMPTY_ARRAY_JSON.equals(messagesJson.trim())) {
      return new ArrayList<>();
    }
    try {
      List<ChatMessageDto> dtos = objectMapper.readValue(messagesJson, MESSAGE_LIST_TYPE_REF);
      return dtos.stream().map(this::fromDto).collect(Collectors.toList());
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize session messages from JSON: {}", messagesJson, e);
      return new ArrayList<>();
    }
  }

  private ChatMessageDto toDto(ChatMessage message) {
    List<ToolCallDto> calls =
        message.getToolCalls() != null
            ? message.getToolCalls().stream()
                .map(c -> new ToolCallDto(c.getId(), c.getName(), c.getArgumentsJson()))
                .collect(Collectors.toList())
            : Collections.emptyList();
    return new ChatMessageDto(
        message.getRole() != null ? message.getRole().name() : MessageType.USER.name(),
        message.getContent(),
        calls,
        message.getToolCallId());
  }

  private ChatMessage fromDto(ChatMessageDto dto) {
    MessageType role;
    try {
      role = dto.role != null ? MessageType.valueOf(dto.role) : MessageType.USER;
    } catch (Exception e) {
      role = MessageType.USER;
    }
    List<ToolCallIntent> toolCalls =
        dto.toolCalls != null
            ? dto.toolCalls.stream()
                .map(c -> new ToolCallIntent(c.id, c.name, c.argumentsJson))
                .collect(Collectors.toList())
            : Collections.emptyList();
    return new ChatMessage(role, dto.content, toolCalls, dto.toolCallId);
  }

  /** 消息序列化 DTO. */
  public static class ChatMessageDto {
    public String role;
    public String content;
    public List<ToolCallDto> toolCalls;
    public String toolCallId;

    /** 默认无参构造器. */
    public ChatMessageDto() {}

    /**
     * 全参构造器.
     *
     * @param role 角色
     * @param content 内容
     * @param toolCalls 工具调用列表
     * @param toolCallId 工具调用 ID
     */
    public ChatMessageDto(
        String role, String content, List<ToolCallDto> toolCalls, String toolCallId) {
      this.role = role;
      this.content = content;
      this.toolCalls = toolCalls;
      this.toolCallId = toolCallId;
    }
  }

  /** 工具调用序列化 DTO. */
  public static class ToolCallDto {
    public String id;
    public String name;
    public String argumentsJson;

    /** 默认无参构造器. */
    public ToolCallDto() {}

    /**
     * 全参构造器.
     *
     * @param id 调用 ID
     * @param name 工具名称
     * @param argumentsJson 参数 JSON
     */
    public ToolCallDto(String id, String name, String argumentsJson) {
      this.id = id;
      this.name = name;
      this.argumentsJson = argumentsJson;
    }
  }
}
