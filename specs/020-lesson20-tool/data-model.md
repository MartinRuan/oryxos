# Data Model: Tool 体系与 Plugin Tool 实体结构

## 1. 统一工具输出：ToolResult

工具执行后的统一响应结构：

```java
public class ToolResult implements Serializable {
    private final boolean success;        // 是否执行成功
    private final String content;         // 执行成功时的返回内容字符串
    private final String errorMessage;    // 执行失败时的错误原因
    private final boolean retryable;       // 失败时是否建议 ReAct Loop 重试

    // 同时提供 JavaBean 风格 getter (isSuccess, getContent...) 
    // 与 Record 风格访问方法 (success(), content(), errorMessage(), retryable())
}
```

## 2. 工具元数据与抽象：OryxTool

所有可供 ReAct Loop 调度的工具需实现的抽象：

```java
public interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();
    ToolResult execute(String inputJson);
    default ToolResult execute(JsonNode input) { ... }
}
```

## 3. MCP 配置实体：McpServerConfig

从 `.oryxos/mcp_servers.yaml` 解析的配置模型：

```yaml
mcp_servers:
  - name: github-mcp
    transport: stdio               # 传输协议：stdio 或 sse
    command: npx                   # 启动命令 (stdio 模式)
    args: ["-y", "@modelcontextprotocol/server-github"]
    url: http://localhost:8080/sse # SSE 模式端点 (可选)
    env:
      GITHUB_PERSONAL_ACCESS_TOKEN: ${GITHUB_TOKEN}
```

Java 映射：

```java
public record McpServerConfig(
    String name,
    String transport,
    String command,
    List<String> args,
    String url,
    Map<String, String> env
) {}
```

## 4. MCP 工具规范实体：McpToolSpec

MCP Server 的 `tools/list` 协议响应返回的工具元数据：

```java
public record McpToolSpec(
    String name,
    String description,
    String inputSchemaJson
) {}
```
