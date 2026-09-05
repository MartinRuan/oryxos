# Contracts: Tool 体系接口规范与协议契约

## 1. ToolRegistry 契约

```java
public class ToolRegistry {
    /** 注册单个工具实例 */
    public void register(OryxTool tool);

    /** 根据工具名称查询已注册工具 */
    public Optional<OryxTool> getTool(String name);

    /** 判断是否存在指定名称的工具 */
    public boolean contains(String name);

    /** 获取当前注册的所有工具列表 */
    public Collection<OryxTool> getAllTools();

    /** 
     * 根据 Profile 声明的 tools 工具名列表精确过滤出可用子集
     * 保证返回的工具列表不多不少，完全匹配声明名单中的有效工具
     */
    public List<OryxTool> filterTools(List<String> allowedToolNames);
}
```

## 2. 内置工具集契约与规范

| 工具名 | 所属类 | 参数规范 (JSON Schema) | 执行行为与安全检查 |
|---|---|---|---|
| `read_file` | `FileTools` | `{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}` | 执行前先调 `Sandbox.enforce(FILE_READ, path)`；返回文件全文内容 |
| `write_file` | `FileTools` | `{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}` | 执行前先调 `Sandbox.enforce(FILE_WRITE, path)`；安全写入目标文件 |
| `list_dir` | `FileTools` | `{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}` | 执行前先调 `Sandbox.enforce(FILE_READ, path)`；返回目录下文件与子目录列表 |
| `shell` | `ShellTools` | `{"type":"object","properties":{"command":{"type":"string"},"args":{"type":"array","items":{"type":"string"}}},"required":["command"]}` | 执行前先调 `Sandbox.enforce(SHELL_COMMAND, command)`；直传 argv、带超时控制，返回 stdout/stderr |
| `http_get` | `HttpTools` | `{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}` | 执行前先调 `Sandbox.enforce(HTTP_REQUEST, url)`；发起 HTTP GET 并返回响应体 |
| `http_post` | `HttpTools` | `{"type":"object","properties":{"url":{"type":"string"},"body":{"type":"string"}},"required":["url"]}` | 执行前先调 `Sandbox.enforce(HTTP_REQUEST, url)`；发起 HTTP POST 并返回响应体 |
| `notify` | `NotifyTools` | `{"type":"object","properties":{"content":{"type":"string"},"channel":{"type":"string"}},"required":["content"]}` | 执行前先调 `Sandbox.enforce(HTTP_REQUEST, url)`；向指定通知渠道推消息 |

## 3. MCP 客户端与适配器契约

```java
public interface McpClient {
    /** 向 MCP Server 查询其暴露的所有工具列表 */
    List<McpToolSpec> listTools();

    /** 转发调用 MCP 工具，返回执行结果字符串 */
    Optional<String> callTool(String name, JsonNode input);
}

public class McpToolAdapter implements OryxTool {
    // 实现 getName/getDescription/getInputSchema 直接映射 McpToolSpec
    // execute(inputJson) -> client.callTool(spec.name(), node)
    // 成功时包装为 ToolResult.success(content)
    // 失败时包装为 ToolResult.failure("MCP 调用失败", true) 可重试
}
```
