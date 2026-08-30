package com.oryxos.cli.command;

import picocli.CommandLine.Command;

/**
 * provider 父命令及 list 子命令：查看支持的 LLM Provider 列表（轻命令）.
 *
 * @author OryxOS Team
 */
@Command(
    name = "provider",
    description = "查看支持的大模型 Provider 列表",
    mixinStandardHelpOptions = true,
    subcommands = {ProviderCommand.ListSubcommand.class})
public class ProviderCommand implements Runnable {

  @Override
  public void run() {
    System.out.println("Use 'oryxos provider list' to view supported LLM providers.");
  }

  /** provider list 子命令. */
  @Command(name = "list", description = "列出系统支持对接的大模型 Provider 列表", mixinStandardHelpOptions = true)
  public static class ListSubcommand implements Runnable {
    @Override
    public void run() {
      System.out.println("Supported LLM Providers in OryxOS:");
      System.out.println(
          "  - deepseek  (DeepSeek Chat & Reasoner, e.g. deepseek-chat, deepseek-reasoner)");
      System.out.println(
          "  - qwen      (Aliyun Tongyi Qwen, e.g. qwen-plus, qwen-turbo, qwen-max)");
      System.out.println("  - kimi      (Moonshot AI Kimi, e.g. moonshot-v1-8k)");
      System.out.println("  - openai    (OpenAI Compatible, e.g. gpt-4o, gpt-4o-mini)");
      System.out.println("  - anthropic (Anthropic Claude, e.g. claude-3-5-sonnet)");
      System.out.println("  - ollama    (Local Ollama Models, e.g. llama3, qwen2.5)");
    }
  }
}
