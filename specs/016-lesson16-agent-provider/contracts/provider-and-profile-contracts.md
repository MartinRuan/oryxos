# Interface Contracts: 第16节 Agent Provider 与 Profile

## 1. ProviderService 接口契约 (`oryxos-provider`)

```java
public interface ProviderService {

    /**
     * 根据 Profile 与 Prompt 发起对话调用，记录审计数据并返回响应.
     *
     * @param sessionId 会话标识（用于审计关联）
     * @param profile Agent 运行配置 Profile
     * @param prompt 发送给模型的 Prompt 上下文（含提示词与可用工具列表）
     * @return 对话响应结果
     * @throws ProviderException 当 Provider 不存在或调用失败时抛出
     */
    ChatResponse chat(String sessionId, Profile profile, ChatRequest prompt);

    /**
     * 直接通过统一请求对象发起模型调用（兼容底层调用模型）.
     *
     * @param request 统一对话请求
     * @return 对话响应结果
     */
    ChatResponse call(ChatRequest request);
}
```

## 2. ToolSchemaAdapter 接口契约 (`oryxos-provider`)

```java
public interface ToolSchemaAdapter {

    /**
     * 将 OryxTool 列表转换为 Spring AI 的 Function Calling Schema 定义.
     * 只做 Schema 描述转换，产物不含任何工具执行逻辑.
     *
     * @param tools OryxTool 工具定义集合
     * @return Spring AI 兼容的工具 Schema 描述列表
     */
    List<ToolDefinition> toSpringAiTools(Collection<OryxTool> tools);
}
```

## 3. ProfileLoader 契约 (`oryxos-core`)

```java
public class ProfileLoader {

    /**
     * 扫描指定目录加载所有 YAML Profile 配置.
     *
     * @param profilesDir profiles 根目录
     * @return 加载解析成功的 Profile 集合
     */
    public List<Profile> loadProfiles(Path profilesDir);

    /**
     * 解析单个 YAML 文本或文件为 Profile 对象，处理 ${ENV_VAR} 解析与校验.
     *
     * @param yamlContent YAML 字符串内容
     * @return 解析后的 Profile
     */
    public Profile parse(String yamlContent);
}
```
