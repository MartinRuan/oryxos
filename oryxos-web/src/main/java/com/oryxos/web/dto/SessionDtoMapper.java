package com.oryxos.web.dto;

import com.oryxos.core.model.ChatMessage;
import com.oryxos.core.model.Session;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Session 领域模型到安全 REST 视图的映射器.
 *
 * @author OryxOS Team
 */
@Component
public class SessionDtoMapper {

  private static final int MAX_HISTORY_SIZE = 100;

  /**
   * 映射会话摘要.
   *
   * @param session 会话
   * @return 会话摘要
   */
  public SessionSummary toSummary(Session session) {
    return new SessionSummary(
        session.getId(),
        session.getProfileName(),
        session.getChannel(),
        session.getUserId(),
        session.getStatus(),
        session.getMessages().size(),
        session.getCreatedAt(),
        session.getLastActiveAt(),
        session.getArchivedAt());
  }

  /**
   * 映射会话详情，仅保留最新 100 条消息并维持原顺序.
   *
   * @param session 会话
   * @return 会话详情
   */
  public SessionDetail toDetail(Session session) {
    List<ChatMessage> allMessages = session.getMessages();
    int fromIndex = Math.max(0, allMessages.size() - MAX_HISTORY_SIZE);
    List<MessageView> messages =
        allMessages.subList(fromIndex, allMessages.size()).stream().map(this::toMessage).toList();
    return new SessionDetail(
        session.getId(),
        session.getProfileName(),
        session.getChannel(),
        session.getUserId(),
        session.getStatus(),
        allMessages.size(),
        session.getCreatedAt(),
        session.getLastActiveAt(),
        session.getArchivedAt(),
        messages);
  }

  private MessageView toMessage(ChatMessage message) {
    return new MessageView(message.getRole().name(), message.getContent(), message.getToolCallId());
  }
}
