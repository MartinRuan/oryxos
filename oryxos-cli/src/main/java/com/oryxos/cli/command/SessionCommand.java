package com.oryxos.cli.command;

import com.oryxos.cli.launcher.SpringContextLauncher;
import com.oryxos.storage.entity.SessionEntity;
import com.oryxos.storage.repository.SessionRepository;
import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/**
 * session 父命令及 list 子命令：查看持久化会话列表.
 *
 * @author OryxOS Team
 */
@Command(
    name = "session",
    description = "查看与管理持久化会话",
    mixinStandardHelpOptions = true,
    subcommands = {SessionCommand.ListSubcommand.class})
public class SessionCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("Use 'oryxos session list' to view active sessions.");
  }

  /** session list 子命令. */
  @Command(name = "list", description = "列出 SQLite 中已持久化的会话列表", mixinStandardHelpOptions = true)
  public static class ListSubcommand implements Runnable {
    @Override
    public void run() {
      try {
        ConfigurableApplicationContext ctx = SpringContextLauncher.getOrCreateContext();
        SessionRepository repository = ctx.getBean(SessionRepository.class);
        List<SessionEntity> sessions = repository.findAll();

        System.out.println("Persisted Sessions (" + sessions.size() + "):");
        if (sessions.isEmpty()) {
          System.out.println("  (No active or archived sessions found)");
        } else {
          for (SessionEntity s : sessions) {
            System.out.printf(
                "  - [%s] profile=%s, channel=%s, user=%s, status=%s, lastActive=%s%n",
                s.getSessionId(),
                s.getProfileName(),
                s.getChannel(),
                s.getUserId(),
                s.getStatus(),
                s.getLastActiveAt());
          }
        }
      } catch (Exception e) {
        System.err.println("[Error] Failed to load sessions: " + e.getMessage());
      }
    }
  }
}
