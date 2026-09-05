package com.oryxos.channel.cli;

import com.oryxos.core.model.Session;
import com.oryxos.core.service.AgentService;
import com.oryxos.core.session.SessionManager;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CLI 控制台交互渠道实现.
 *
 * <p>提供交互式问答交互循环，维护 Session，每行输入委托 AgentService ReAct 循环处理，并在输入 /quit 时优雅退出.
 *
 * <p>中文输入编码策略：使用系统原生编码（Windows 中文版为 GBK）读取控制台输入。 不要强制 UTF-8，否则会导致 Windows 控制台输入法产出的 GBK 字节被错误解码为乱码。
 * Java String 内部统一使用 Unicode，HTTP 传输层自动使用 UTF-8 发送给 LLM API。
 *
 * @author OryxOS Team
 */
@Component
public class CliChannel {

  private static final Logger log = LoggerFactory.getLogger(CliChannel.class);
  private static final String CHANNEL_NAME = "cli";
  private static final String QUIT_COMMAND = "/quit";
  private static final String EXIT_COMMAND = "/exit";

  private final AgentService agentService;
  private final SessionManager sessionManager;

  /**
   * 构造 CLI 渠道组件.
   *
   * @param agentService Agent 业务核心服务
   * @param sessionManager 会话生命周期管理器
   */
  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this.agentService = Objects.requireNonNull(agentService, "agentService must not be null");
    this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
  }

  /**
   * 启动 CLI 控制台交互循环（默认标准输入输出）.
   *
   * @param profileName 绑定的 Profile 名称
   */
  public void run(String profileName) {
    run(profileName, System.in, System.out);
  }

  /**
   * 使用指定的输入输出流启动交互式对话循环（使用自动解析的控制台字符集）.
   *
   * @param profileName 绑定的 Profile 名称
   * @param in 输入流
   * @param out 输出流
   */
  public void run(String profileName, InputStream in, PrintStream out) {
    run(profileName, in, out, resolveConsoleCharset());
  }

  /**
   * 使用指定的输入输出流与字符集启动交互式对话循环.
   *
   * @param profileName 绑定的 Profile 名称
   * @param in 输入流
   * @param out 输出流
   * @param charset 输入流解码所使用的字符编码
   */
  public void run(String profileName, InputStream in, PrintStream out, Charset charset) {
    Objects.requireNonNull(profileName, "profileName must not be null");
    Objects.requireNonNull(in, "in must not be null");
    Objects.requireNonNull(out, "out must not be null");

    Charset actualCharset = charset != null ? charset : resolveConsoleCharset();
    String currentUser = System.getProperty("user.name", "default");
    Session session = sessionManager.getOrCreate(CHANNEL_NAME, currentUser, profileName);
    log.info("CliChannel started with session [{}] for profile [{}]", session.getId(), profileName);
    log.info("CliChannel input charset: {}", actualCharset.name());

    BufferedInputStream bis =
        in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in);

    try {
      while (true) {
        out.print("> ");
        out.flush();
        String line = readLine(bis, actualCharset);

        if (line == null) {
          break;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        if (QUIT_COMMAND.equalsIgnoreCase(trimmed) || EXIT_COMMAND.equalsIgnoreCase(trimmed)) {
          log.info("CliChannel received quit command, exiting session [{}]", session.getId());
          break;
        }

        if ("/clear".equalsIgnoreCase(trimmed) || "/reset".equalsIgnoreCase(trimmed)) {
          session.getMessages().clear();
          sessionManager.save(session);
          log.info("CliChannel session messages cleared for session [{}]", session.getId());
          out.println("[会话已重置，历史记录已清空]");
          out.flush();
          continue;
        }

        log.info("CliChannel received user input: [{}]", trimmed);

        try {
          String reply = agentService.process(session, trimmed);
          out.println(sanitizeForConsole(reply, actualCharset));
          out.flush();
        } catch (Exception e) {
          log.error("Error processing user input in CLI session [{}]", session.getId(), e);
          out.println("[Error] " + e.getMessage());
          out.flush();
        }
      }
    } catch (Exception e) {
      log.error("CliChannel stream reading error in session [{}]", session.getId(), e);
    }
  }

  private static final Charset GBK_CHARSET = Charset.forName("GBK");

  /**
   * 从输入流按行读取字节并自适应解码（自动兼容 UTF-8 与 GBK，解决 Windows 控制台 IME 输入乱码问题）.
   *
   * @param in 输入流
   * @param preferredCharset 首选字符集
   * @return 读取到的单行字符串，若流结束且无数据返回 null
   * @throws IOException 输入流读取异常
   */
  private static String readLine(BufferedInputStream in, Charset preferredCharset)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        break;
      }
      if (b != '\r') {
        baos.write(b);
      }
    }
    if (b == -1 && baos.size() == 0) {
      return null;
    }
    return decodeInputBytes(baos.toByteArray(), preferredCharset);
  }

  /**
   * 自适应解码输入字节数组，先以 preferredCharset 尝试严格解码，若格式非法则回退到备用编码（UTF-8 <-> GBK）.
   *
   * @param bytes 字节数组
   * @param preferredCharset 首选字符集
   * @return 解码后的字符串
   */
  private static String decodeInputBytes(byte[] bytes, Charset preferredCharset) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    Charset primary = preferredCharset != null ? preferredCharset : StandardCharsets.UTF_8;
    Charset secondary =
        StandardCharsets.UTF_8.equals(primary) ? GBK_CHARSET : StandardCharsets.UTF_8;

    try {
      CharsetDecoder decoder =
          primary
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      try {
        CharsetDecoder secDecoder =
            secondary
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return secDecoder.decode(ByteBuffer.wrap(bytes)).toString();
      } catch (CharacterCodingException ex) {
        return new String(bytes, primary);
      }
    }
  }

  /**
   * 解析控制台实际使用的字符编码.
   *
   * <p>优先读取 {@code oryxos.console.charset} 系统属性（由 oryxos.ps1 从 PowerShell {@code
   * [Console]::InputEncoding.WebName} 探测后通过 {@code -D} 传入）。 如果该属性不存在或无法解析，则回退到 JVM 默认字符集。
   *
   * @return 控制台输入流应使用的字符编码
   */
  private static Charset resolveConsoleCharset() {
    String charsetName = System.getProperty("oryxos.console.charset");
    if (charsetName != null && !charsetName.isBlank()) {
      try {
        Charset resolved = Charset.forName(charsetName);
        log.debug("Using console charset from oryxos.console.charset: {}", resolved.name());
        return resolved;
      } catch (Exception e) {
        log.warn(
            "Invalid oryxos.console.charset value [{}], falling back to default", charsetName, e);
      }
    }
    Charset fallback = Charset.defaultCharset();
    log.debug("No oryxos.console.charset set, using JVM default charset: {}", fallback.name());
    return fallback;
  }

  /**
   * 过滤控制台无法编码的特殊字符（如 4 字节 Emoji 在 GBK 代码页下会自动降级为问号 '?'）.
   *
   * @param text 待输出给控制台的文本
   * @param charset 控制台输出字符编码
   * @return 过滤不可映射字符后的整洁文本
   */
  private static String sanitizeForConsole(String text, Charset charset) {
    if (text == null
        || text.isEmpty()
        || charset == null
        || StandardCharsets.UTF_8.equals(charset)) {
      return text;
    }
    CharsetEncoder encoder = charset.newEncoder();
    StringBuilder sb = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      int codePoint = text.codePointAt(i);
      int charCount = Character.charCount(codePoint);
      String s = text.substring(i, i + charCount);
      if (encoder.canEncode(s)) {
        sb.append(s);
      }
      i += charCount;
    }
    return sb.toString();
  }
}
