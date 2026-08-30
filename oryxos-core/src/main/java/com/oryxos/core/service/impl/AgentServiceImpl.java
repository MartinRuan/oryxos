package com.oryxos.core.service.impl;

import com.oryxos.core.context.ProfileContext;
import com.oryxos.core.model.Profile;
import com.oryxos.core.model.Session;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 门面服务标准实现.
 *
 * <p>核心职责： 1. 寻址 Profile 并设置当前线程 ProfileContext 2. 驱动 ReAct 循环执行任务 3. 执行完毕后保存 Session 4. 在 finally
 * 块中绝对清除 ProfileContext，防止虚拟线程池污染
 *
 * @author oryxos
 */
@Service
public class AgentServiceImpl implements AgentService {

  private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

  private final ProfileRegistry profileRegistry;
  private final ReActLoop reActLoop;
  private final SessionManager sessionManager;

  /**
   * 构造函数注入各协同组件.
   *
   * @param profileRegistry Profile 注册中心
   * @param reActLoop 自实现 ReAct 循环引擎
   * @param sessionManager 会话管理器
   */
  public AgentServiceImpl(
      ProfileRegistry profileRegistry, ReActLoop reActLoop, SessionManager sessionManager) {
    this.profileRegistry =
        Objects.requireNonNull(profileRegistry, "profileRegistry must not be null");
    this.reActLoop = Objects.requireNonNull(reActLoop, "reActLoop must not be null");
    this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
  }

  @Override
  public String process(Session session, String userMessage) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(userMessage, "userMessage must not be null");

    Profile profile = profileRegistry.getRequiredProfile(session.getProfileName());

    ProfileContext.set(profile);
    try {
      String response = reActLoop.run(session, userMessage, profile);
      sessionManager.save(session);
      return response;
    } catch (Exception e) {
      log.error(
          "Error processing message for session {} with profile {}",
          session.getId(),
          profile.getName(),
          e);
      throw e;
    } finally {
      ProfileContext.clear();
    }
  }
}
