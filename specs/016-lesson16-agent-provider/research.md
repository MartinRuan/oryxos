# Research Decisions: 第16节 Agent Provider 与 Profile

## 1. 显式映射 vs 类型扫描

- **Decision**: 使用 `Map<String, ChatModel>` 维护 `provider name -> ChatModel` 的显式映射表，通过 `ProviderRegistry` 进行管理与寻址。
- **Rationale**: Spring 容器中可能存在多个同为 `ChatModel` 类型的 Bean（例如通义千问、DeepSeek 等），仅靠 Bean 类型扫描无法区分目标，必须靠唯一的 Provider Name 显式寻址。
- **Alternatives considered**: 按 `@Qualifier` 扫描——容易因命名不规范产生漏配；显式注册表更清晰可控。

## 2. 工具 Function Calling 协议转换与执行权控制

- **Decision**: 实现 `ToolSchemaAdapter` / `FunctionCallingAdapter`，将 `OryxTool` 的 JSON Schema 转换为 Spring AI 工具描述元数据，但在底层请求中严格设置 `autoExecuteTools=false`。
- **Rationale**: Spring AI 默认的自动工具执行会接管循环并绕过沙箱，导致工具被重复调用两次。OryxOS 的核心原则是执行权完全由 `ReActLoop` + `ToolExecutor` 掌控，Provider 仅做 Schema 生成与协议抹平。
- **Alternatives considered**: 使用 Spring AI `ChatClient` 自动回调——违背项目技术宪章原则二，直接否决。

## 3. Profile 领域模型全字段与加载机制

- **Decision**: 在 `oryxos-core` 定义全字段 `Profile` 类（`name`, `description`, `identity`, `provider`, `tools`, `skills`, `mcp_servers`, `channels`, `notify_channels`, `schedules`, `bootstrap`, `settings`）。使用 SnakeYAML 加载 `.oryxos/profiles/*.yaml`，支持 `${ENV_VAR}` 占位符解析。损坏文件记录日志并不阻断启动。
- **Rationale**: Profile 是后续各节能力消费的配置基石，第 16 节一次性建全全字段，后续各节无需再动核心结构。

## 4. 审计表 Day One 落库与手工建表脚本

- **Decision**: 在 `oryxos-storage` 定义 `LlmCallEntity` 与 `LlmCallRepository`，包含 `success` 与 `error_message` 字段。SQLite 表结构采用手工 SQL 脚本管理。
- **Rationale**: 审计是企业级 Agent OS 的非妥协能力，成功与失败事故均需记录耗时和状态。SQLite 的 `ALTER TABLE` 功能受限，严禁依赖 `hibernate.ddl-auto=update`。
