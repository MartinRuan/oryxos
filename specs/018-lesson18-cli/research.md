# Technical Research & Architecture Decisions: 第18节 CLI 与会话持久化

## 1. 架构定位与轻重命令分流

### 决策
- Picocli 作为整个 OryxOS 的 CLI 统一入口，挂载 12 个子命令：
  - 轻命令（文件/只读）：`init`, `profile list`, `profile create`, `profile show`, `profile delete`, `provider list`, `tool list`, `session list`, `status`。
  - 重命令（调模型/跑引擎）：`chat`, `serve`, `gateway`。
- 轻命令**不启动 Spring 上下文**，直接使用 JDK `java.nio.file` 或基础类库读取 `.oryxos/` 目录结构，保证毫秒级响应。
- 重命令按需引导启动 Spring Boot 上下文，执行完整 AgentService / ReActLoop。

### 理由
- Spring Boot 在 JDK 21 下冷启动约 2~4 秒。对于运维查看 `profile list` 或 `status` 类即时命令，等待数秒体验极差。
- 轻重命令分流在入口层判定，结构清晰，互不干扰。

---

## 2. 重命令 Spring 容器与 JPA 跨模块扫描

### 决策
- 重命令启动类 `OryxCliApplication` 或在引导 Spring 时，显式声明：
  - `@SpringBootApplication(scanBasePackages = "com.oryxos")`
  - `@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")`
  - `@EntityScan(basePackages = "com.oryxos.storage.entity")`

### 理由
- 坑点规避：`@SpringBootApplication` 只扫描组件 Bean，而 Spring Data JPA 的 `@EnableJpaRepositories` 与 `@EntityScan` 默认只扫描主类所在包。
- `oryxos-cli` 与 `oryxos-storage` 是不同的 Maven 模块及 Java 包路径，若不显式声明扫描包，Spring Boot 启动时会出现 `Found 0 JPA repository interfaces` 报错。

---

## 3. 会话 ID 格式与 SessionManager 统一收敛

### 决策
- `SessionManager` 接口定义在 `oryxos-core`，统一收敛会话 ID 拼接公式：
  `sessionId = channel + ":" + userId + ":" + profileName`（或标准化三元组哈希/字符串）。
- 入口（CLI 传 `"cli"`、Web 传 `"web"`、定时任务传 `"scheduler"`）只能调用 `getOrCreate(channel, user, profileName)`，严禁外部拼接 ID 字符串。
- `SessionManager` 实现基于 `JpaSessionManager`（放 `oryxos-storage`），并保留 `InMemorySessionManager`（放 `oryxos-core`）作为离线回退。

### 理由
- 规避会话串号与口径不一（宪法原则与架构契约）：若各入口自拼 ID，易因分隔符不同导致同一个人的多轮历史无法串联。

---

## 4. 对话历史 JSON 序列化与 SQLite 持久化

### 决策
- `SessionEntity` 将 `List<ChatMessage>` 序列化为单个 JSON 文本列 `messages_json` 存储。
- 使用 Jackson ObjectMapper 序列化/反序列化，支持 `ChatMessage` 的所有类型（USER, ASSISTANT, TOOL, SYSTEM）及 `toolCalls` 嵌套结构。
- 数据库建表使用手写 SQL 脚本 `schema.sql`，禁止依赖 Hibernate `ddl-auto=update`。

### 理由
- 核心阶段单表单列整存整取已足够支撑单机数万次对话，避免早期过度设计拆分子表。
- SQLite 对 `ALTER TABLE` 支持受限，手写 SQL 能精确掌控 DDL。
