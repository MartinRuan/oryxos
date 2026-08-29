<!--
Sync Impact Report
- Version change: 1.0.1 → 1.1.0 (Core Principles Consolidation & Full Alignment)
- List of modified principles:
  - 一、单二进制与自包含部署 (Single Binary & Self-Contained Deployment)
  - 二、自实现 ReAct 循环 (Controlled ReAct Loop Engine)
  - 三、Spring AI 严格限定两项职责 (Spring AI Boundary & Tool Execution Control)
  - 四、Provider 显式映射 (Explicit Provider Mapping)
  - 五、一个目录 = 一个 Agent 与渐进式披露 (Declarative Agent Directory & Progressive Disclosure)
  - 六、审计表 Day One 写入与全链路可观测 (Day One Audit Persistence & Observability)
  - 七、禁用 SecurityManager 与落实白名单沙箱 (Application-Layer Whitelist Sandbox)
  - 八、同步阻塞执行模型与虚拟线程并发 (Synchronous Virtual Thread Concurrency)
  - 九、Tool 模块三合一 (Consolidated Tool Architecture)
  - 十、严格质量门禁与测试驱动开发 (Strict Quality Gates & TDD Verification)
- Added sections: None
- Removed sections: None
- Follow-up TODOs: None
-->

# OryxOS 项目技术宪章

## 核心原则 (Core Principles)

### 一、单二进制与自包含部署 (Single Binary & Self-Contained Deployment)
OryxOS **必须** 打包为单一且自包含的可执行 fat JAR（基于 JDK 21 + Spring Boot 3.x）进行分发与部署。系统默认持久化 **必须** 仅依赖内置嵌入式 SQLite 以及本地文件系统（`.oryxos/agents/`、`.oryxos/memory/MEMORY.md`）。所有外部依赖（LLM 厂商 API、MCP Server、自托管 Mem0 等）均位于系统边界之外，并通过解耦的可插拔适配器接口进行集成。OryxOS 核心系统在无需任何外部独立数据库（如 MySQL/PostgreSQL）、Redis 缓存或消息队列（MQ）基础设施的前提下，**必须** 能够独立启动并正常运行。

### 二、自实现 ReAct 循环 (Controlled ReAct Loop Engine)
ReAct 推理循环是 OryxOS 的核心大脑引擎，**必须** 由项目原生自研实现（`ReActLoop`），严禁委托给第三方黑盒 Agent 框架抽象（如 Spring AI 的自动工具调用）。核心循环逻辑保持数十行清晰 Java，完整掌控 Agent 推理、工具调用与结果回灌全过程。

### 三、Spring AI 严格限定两项职责 ⚠️ (Spring AI Boundary & Tool Execution Control)
Spring AI 在 OryxOS 中 **严格限制** 仅承担两项职责：
1. LLM Provider 协议转换（抹平 OpenAI、Anthropic、Gemini、通义千问等格式差异）。
2. `@Tool` 注解的 JSON Schema 生成。
**必须显式禁用** Spring AI 自带的 Tool 自动调用执行机制；所有 Tool 的实际调度、沙箱安全校验、执行及结果回填，**必须** 完全由 OryxOS 自有的 `ToolExecutor` 统一掌控，杜绝工具被重复调用的风险。

### 四、Provider 必须显式映射 (Explicit Provider Mapping)
多 Provider 并存时，**严禁** 依靠扫描 Spring 容器中 `ChatModel` Bean 类型来区分 Provider（因各实现 Bean 类型相同）。系统 **必须** 维护显式的 `provider name → ChatModel` 映射表，通过 Profile 中的 `provider.name` 显式路由。

### 五、一个目录 = 一个 Agent 与渐进式披露 (Declarative Agent Directory & Progressive Disclosure)
OryxOS 严格遵循“一个目录 = 一个 Agent”的设计范式（`.oryxos/agents/<name>/`）。每个业务 Agent **必须** 声明式定义在其专属目录中（通过 `AGENT.md` 的 frontmatter 声明运行时 profile 与定时调度 schedules，正文声明 Agent 人格与任务指令，配合绑定的 skills 相对软连接与 scripts 脚本）。业务 Agent 必须通过配置与 Markdown 沉淀，**严禁** 通过编写 Java 业务代码来定义 Agent。上下文组装 **必须** 遵循渐进式披露：System Prompt 仅预载 `AGENT.md` 正文与 Skill 描述元数据；Skill 详细正文与附带脚本 **必须** 由 LLM 根据任务需要，通过底座基础工具（`read_file`、`shell`）按需读取和执行。

### 六、审计表 Day One 写入与全链路可观测 (Day One Audit Persistence & Observability)
全链路审计与运维可观测性是 OryxOS 的非妥协底层基石。系统每次执行 Tool 调用 **必须** 实时写入 `tool_invocations` 审计表；每次调用大模型（包含 Token 消耗、耗时、Provider、模型名称）**必须** 实时写入 `llm_calls` 审计表，严禁以“仅打日志”替代落库。系统日志 **必须** 全面结构化输出（SLF4J + Logstash JSON，并在 MDC 中注入 `traceId`），严禁使用 `System.out.println`。系统 **必须** 通过 Spring Boot Actuator 与 Prometheus 暴露标准健康探针与监控指标端点（`/actuator/health`、`/actuator/prometheus`）。

### 七、禁用 Java SecurityManager 与落实白名单沙箱 (Application-Layer Whitelist Sandbox)
鉴于 Java `SecurityManager` 在 JDK 17+ 废弃且在 JDK 21+ 已不可用，系统 **必须** 从 Day One 起在应用层通过 `Sandbox` / `WhitelistSandbox` 落地白名单沙箱：
- **文件**：路径白名单（强制使用 `toRealPath()` 校验防止符号链接穿越与越界）。
- **Shell**：可执行命令精确白名单 + 参数 argv 直传（不解释 Shell 语法）。
- **HTTP / 网络**：域名与 URL 通配符白名单 + SSRF 防护。
- **SMTP**：`host:port` 端点白名单。

### 八、同步阻塞执行模型与虚拟线程并发 (Synchronous Virtual Thread Concurrency)
从 HTTP 接入层（Spring MVC）、Agent 推理引擎到 Tool 工具执行的全链路请求处理流程，**必须** 采用直观、清晰的同步阻塞编程模型，并全面启用 Java 21 虚拟线程（`spring.threads.virtual.enabled=true`）。核心调用链路中 **严禁** 引入响应式编程框架（如 Spring WebFlux、Project Reactor），以保持异常调用栈清晰易调试、代码直观简洁，同时保障单机实例轻松支撑数千并发会话。

### 九、Tool 模块三合一 (Consolidated Tool Architecture)
内置 Tool（文件/Shell/HTTP/Notify）、MCP Client 集成适配器与 Sandbox 检查器 **必须** 统一合并收敛在 `oryxos-tool` 单一模块中，不拆散多模块。`AGENT.md` 与上下文加载逻辑归属于 `oryxos-core` 的 `ContextLoader`。

### 十、严格质量门禁与测试驱动开发 (Strict Quality Gates & TDD Verification)
所有代码入库与特性交付 **必须** 严格遵守自动化质量与安全门禁。代码合入前 **必须** 100% 通过 Spotless 代码格式化（Google Java Format）、阿里巴巴 Java 开发规约（P3C-PMD）、Checkstyle、静态安全扫描（SpotBugs + Find Security Bugs、PMD）以及 OWASP 第三方依赖漏洞扫描（Dependency-Check）。团队 **必须** 坚持测试驱动开发（TDD），所有核心功能必须配备完整的单元测试与契约测试，CI 流水线遇任何质量门禁报警 **必须** 阻断合并。

## 技术栈与架构约束 (Technology Stack & Architecture Constraints)

1. **运行时与核心框架**：Java 21 (LTS)、Spring Boot 3.x、Spring AI Alibaba、Picocli (命令行框架)。
2. **模块化架构**：严格遵循 11 个 Maven 多模块分层架构：
   - `oryxos-core`：核心引擎与基础抽象（`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`OryxTool` 等）。
   - `oryxos-provider`：Provider 抽象及 provider name 到底层 `ChatModel` 的显式映射管理。
   - `oryxos-memory`：统一 `MemoryService` 门面、`LongTermMemoryStore` 后端（Markdown、SQLite、Mem0）及记忆工具。
   - `oryxos-knowledge`：知识库接入、文档解析切分索引流水线与 RRF 混合检索。
   - `oryxos-tool`：内置工具集、MCP Client 集成、`Sandbox` 沙箱检查及 `NotifyTools` 出站通知。
   - `oryxos-channel-cli` / `oryxos-channel-feishu`：CLI 交互渠道与飞书等入站渠道适配器。
   - `oryxos-web`：Spring MVC REST API 服务、OpenAPI 接口文档及全局统一异常处理 `GlobalExceptionHandler`。
   - `oryxos-storage`：JPA 实体、SQLite 仓库及 Session、审计数据持久化。
   - `oryxos-cli`：Picocli 命令行主入口及 12 个运维/管理子命令。
   - `oryxos-boot`：Spring Boot 启动入口模块与 fat JAR 打包聚合。
3. **密钥与配置安全规范**：源代码及默认配置文件中 **严禁** 硬编码任何 API 密钥、访问令牌或敏感凭证。所有敏感配置项 **必须** 使用环境变量占位符注入（例如 `${OPENAI_API_KEY}`）。

## 开发流程与质量门禁 (Development Workflow & Quality Gates)

1. **Spec 驱动开发体系**：所有新特性与重构 **必须** 遵循 Spec Kit 规范流程推进（`spec.md` 需求定义 → `plan.md` 技术方案设计 → `tasks.md` 依赖任务拆解 → 测试先行与代码实现）。
2. **静态代码与安全扫描指令**：
   - `mvn spotless:check`（Google Java 格式校验）
   - `mvn checkstyle:check`（代码风格规范）
   - `mvn pmd:check` & `mvn pmd:cpd-check`（阿里 P3C 编码规约与代码重复度）
   - `mvn spotbugs:check`（潜在 Bug 与 FindSecBugs 安全漏洞扫描）
   - `mvn dependency-check:check`（OWASP 第三方依赖 CVE 漏洞检测）
3. **持续集成 (CI)**：每次提交与合并请求（PR）**必须** 通过跨模块 `mvn clean verify` 完整构建与自动化测试套件。

## 治理规范 (Governance)

本宪章是 OryxOS 项目的技术最高治理准则。所有日常开发、架构评审、文档编写与代码实现，均受本宪章约束。

1. **修订程序**：任何对核心原则、架构约束或质量规范的修改，**必须** 提交正式修订提案，附带修改理由、版本号升级说明以及 Sync Impact Report 同步影响报告。
2. **版本管理规则**：
   - **主版本号 (MAJOR - X.0.0)**：核心原则的废除、根本性重构或不向下兼容的重大治理调整。
   - **次版本号 (MINOR - x.Y.0)**：新增原则、新增章节或对架构指导方针的实质性扩充。
   - **修订版本号 (PATCH - x.y.Z)**：文字表述优化、翻译本地化、勘误澄清或格式规范微调。
3. **合规审查**：所有技术设计文档（`plan.md`）、代码审查（Code Review）以及 CI 自动构建流水线，**必须** 严格校验是否符合本宪章要求。

**Version**: 1.1.0 | **Ratified**: 2026-08-29 | **Last Amended**: 2026-08-29
