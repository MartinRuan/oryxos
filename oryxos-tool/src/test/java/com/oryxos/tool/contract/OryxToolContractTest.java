package com.oryxos.tool.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oryxos.core.OryxTool;
import com.oryxos.core.context.ProfileContext;
import com.oryxos.tool.ToolRegistry;
import com.oryxos.tool.builtin.FileTools;
import com.oryxos.tool.builtin.HttpTools;
import com.oryxos.tool.builtin.NotifyTools;
import com.oryxos.tool.builtin.ShellTools;
import com.oryxos.tool.notify.NotifyChannelAdapter;
import com.oryxos.tool.sandbox.Sandbox;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * OryxTool 核心契约三件套测试（验收 harness 自动化守门点）.
 *
 * @author OryxOS Team
 */
class OryxToolContractTest {

  static Stream<OryxTool> allRegisteredTools() {
    Sandbox sandbox = mock(Sandbox.class);
    NotifyChannelAdapter adapter = mock(NotifyChannelAdapter.class);
    ProfileContext profileContext = new ProfileContext();

    ToolRegistry registry = new ToolRegistry();

    FileTools fileTools = new FileTools(sandbox);
    for (OryxTool tool : fileTools.getTools()) {
      registry.register(tool);
    }

    ShellTools shellTools = new ShellTools(sandbox);
    registry.register(shellTools);

    HttpTools httpTools = new HttpTools(sandbox);
    for (OryxTool tool : httpTools.getTools()) {
      registry.register(tool);
    }

    NotifyTools notifyTools = new NotifyTools(sandbox, adapter, profileContext);
    registry.register(notifyTools);

    return registry.getAllTools().stream();
  }

  @ParameterizedTest
  @MethodSource("allRegisteredTools")
  @DisplayName("每个工具的契约三件套都不能缺（name/description/inputSchema）")
  void 每个工具的契约三件套都不能缺(OryxTool tool) {
    assertThat(tool.getName()).as("工具名称不能为空，LLM 靠它点名要调谁").isNotNull().isNotBlank();

    assertThat(tool.getDescription()).as("工具描述不能为空，LLM 靠它理解何时调用").isNotNull().isNotBlank();

    assertThat(tool.getInputSchema())
        .as("入参 JSON Schema 不能为空，缺了它 Provider 翻译 Function Calling 时直接卡死")
        .isNotNull()
        .isNotBlank();
  }
}
