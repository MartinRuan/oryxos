# OryxOS — AI 编程助手与开发者指南

OryxOS 是用 Java (JDK 21) 实现的面向企业场景的 **Distributed AI Agent OS**。装在企业自己的 K8s 或服务器上，作为统一底座运行多个业务 Agent，共享渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业自己的基础设施，不锁任何云生态。

长期目标：走进 Apache 基金会，成为 Apache 顶级项目。

> 详细文档索引：`docs/DemandAnalysis.md`（需求）、`docs/TechnicalSolution.md`（技术方案）、`docs/IndustryResearch.md`（业界调研）、`docs/AiProgrammingGuide.md`（AI 编程指南）、`docs/oryxos.md`（项目定位）、`.specify/memory/constitution.md`（技术宪章）。

---

## 1. Spec-Driven Development (SDD) 规范

本项目严格使用 [Spec Kit](https://github.com/github/spec-kit) 进行规范驱动开发。任何功能开发、架构重构或模块改造均须遵循以下工作流：

1. `/speckit-constitution` - 确立与维护项目宪章原则
2. `/speckit-specify` - 编写/更新功能基线规范 (`spec.md`)
3. `/speckit-clarify` - (可选) 澄清模糊需求与技术边界
4. `/speckit-plan` - 生成模块化技术实施计划 (`plan.md`)
5. `/speckit-checklist` - (可选) 校验需求质量与完整性
6. `/speckit-tasks` - 拆解具有依赖关系的行动任务清单 (`tasks.md`)
7. `/speckit-analyze` - (可选) 跨产物一致性校验
8. `/speckit-implement` - 执行代码开发与 TDD 落地
9. `/speckit-converge` - 比对代码库与规范，补齐未尽任务

---

## 2. 技术栈选型

| 组件 | 选型 | 约束说明 |
| :--- | :--- | :--- |
| **语言 / 运行时** | Java 21 (LTS) | 必须启用 Virtual Threads 处理并发 |
| **基础框架** | Spring Boot 3.x | 单体应用架构，打包为 fat JAR |
| **LLM 调用** | Spring AI Alibaba | **仅用** 协议转换 + `@Tool` Schema 生成，**禁用** 自动 tool 执行 |
| **HTTP 服务** | Spring MVC + 虚拟线程 | 同步阻塞编程模型，`spring.threads.virtual.enabled=true` |
| **命令行框架** | Picocli | 12 个运维与交互子命令 |
| **YAML 解析** | SnakeYAML | Profile 与 Agent 配置解析 |
| **持久化** | SQLite + Spring Data JPA | 嵌入式单二进制持久化，手写迁移/脚本 |
| **日志框架** | Logback + SLF4J | 结构化 JSON 输出，MDC 注入 `traceId`，禁止 `System.out` |
| **构建工具** | Maven 多模块 | 依赖版本集中管理，严格执行安全与格式门禁 |

---

## 3. 模块结构（11 个子模块）

```
oryxos/
├── oryxos-core          # 核心抽象：OryxTool 接口、Session、Profile、ContextLoader、
│                        #   ReActLoop、PromptBuilder、ToolExecutor、AgentService
├── oryxos-provider      # 能力一：ProviderService、Function Calling 适配、
│                        #   多 Provider 显式映射
├── oryxos-memory        # 能力三：MemoryService 门面、LongTermMemory 三档后端、
│                        #   MemoryRecallEngine 三路召回 + MemoryVectorIndex、
│                        #   MemoryTools (save/recall)
├── oryxos-knowledge     # 知识库：LocalKnowledgeBackend、解析/切分/向量化流水线、
│                        #   双路召回 + RRF 检索、ChunkStore 可插拔存储、KnowledgeTools
├── oryxos-tool          # 能力四：内置 Tool (文件/Shell/HTTP/Notify)、MCP Client、
│                        #   ToolRegistry、Sandbox (WhitelistSandbox)
├── oryxos-channel-cli   # CLI Channel：oryxos chat 交互实现
├── oryxos-channel-feishu # 飞书 IM 入站渠道：oapi-sdk 长连接接收、@ 判定与剥离、分段+沙箱
├── oryxos-web           # 能力五：WebServer、ApiController (10 个核心端点)、
│                        #   GlobalExceptionHandler、OpenAPI
├── oryxos-storage       # 持久化：SQLite、SessionRepository、
│                        #   ToolInvocationRepository、LlmCallRepository
├── oryxos-cli           # 命令行入口：Picocli 主入口、12 个子命令、ConfigLoader
└── oryxos-boot          # Spring Boot 启动模块：主类、自动配置、依赖聚合
```

> **架构演进原则**：模块划分跟随 Agent 能力域，通过接口解耦。跨模块契约（接口 + 值对象）统一定义在 `oryxos-core`，由下游模块实现（依赖倒置），**严禁模块间循环依赖**。

---

## 4. 不可违背的核心原则 (Constitution)

所有 AI Agent 与人类开发者在编写代码时必须严格遵守以下原则：

### 原则一：自实现 ReAct Loop
`ReActLoop` 必须自己实现，**严禁** 使用 Spring AI 的 Agent 抽象（如 `ChatClient.prompt().call()` 的自动工具执行）。核心循环保持数十行 Java，完整掌控 Agent 推理与执行机制。

### 原则二：Spring AI 严格限定两项职责 ⚠️
Spring AI 在 OryxOS 里只做两件事：
1. LLM Provider 协议转换（抹平 OpenAI、Anthropic、Gemini、通义千问等各家差异）
2. `@Tool` 注解的 JSON Schema 生成

**必须禁用** Spring AI 的自动 Tool 执行机制。Tool 调度和执行完全由 `ReActLoop` + `ToolExecutor` 控制，防止工具被重复调用。

### 原则三：Provider 必须显式映射
多 Provider 并存时，**不得** 靠扫描 Spring 容器里的 `ChatModel` Bean 类型来区分 Provider（因为 Bean 类型相同）。必须维护 `provider name → ChatModel` 的显式映射表：
```java
Map<String, ChatModel> providerMap = Map.of(
    "deepseek", deepseekChatModel,
    "qwen",     qwenChatModel,
    "kimi",     kimiChatModel
);
```

### 原则四：一个目录 = 一个 Agent；Skill 本地软连接绑定与渐进式披露
- **一个目录 = 一个 Agent**：`.oryxos/agents/<name>/` 包含 `AGENT.md`（frontmatter 运行配置 + 正文任务指令）+ 可选 `skills/` + `scripts/` + `REFERENCE.md`。`AgentLoader.deriveProfile(agentDir)` 把 frontmatter 派生成 `Profile`。
- 公共 Skill 实体存放在 `.oryxos/skills/<name>/`。Agent 可见 Skill 仅通过 `.oryxos/agents/<agent>/skills/<name>` 下的**相对软连接**表达。
- 渐进式披露：每轮 Prompt 仅注入绑定 Skill 的 `name + description + 本地读取路径`；模型命中后调用 `read_file` 读取 `SKILL.md` 正文，附属脚本/参考按需取用。

### 原则五：审计表 Day One 写入
`tool_invocations` 和 `llm_calls` 两张审计表**核心阶段就必须写入**。不得以“日志够了”为由跳过落库，全链路可审计是 OryxOS 的核心能力。

### 原则六：禁用 Java SecurityManager，落实应用层白名单沙箱
Java `SecurityManager` 在 JDK 17+ 废弃、JDK 21 已不可用。必须通过 `Sandbox` / `WhitelistSandbox` 落地：
- **文件**：路径白名单（`toRealPath()` 防止符号链接越界）
- **Shell**：命令可执行文件精确白名单 + argv 直传（不解释 Shell 语法）
- **HTTP / 网络**：域名与 URL 通配符白名单 + SSRF 防护
- **SMTP**：`host:port` 端点白名单

### 原则七：同步阻塞执行模型
核心阶段全程采用同步阻塞模型，配合 Java 21 Virtual Thread 处理高并发。**不引入** Reactor / WebFlux / CompletableFuture 等异步响应式编程模型。

### 原则八：Tool 模块三合一
内置 Tool、MCP Client、Sandbox Checker 合并在 `oryxos-tool` 模块。`AGENT.md` 加载归 `oryxos-core` 的 `ContextLoader`。

---

## 5. 工作区结构规范（运行时 `.oryxos/`）

```
.oryxos/
├── agents/             # 每个子目录 = 一个 Agent (AGENT.md + skills/ 软连接 + scripts/)
├── skills/             # 公共 Skill 实体库：每个子目录 = 一个 Skill (SKILL.md)
├── memory/
│   └── MEMORY.md       # 长期记忆 (Agent 通过 save_memory 写入，禁止手动修改)
├── sessions/           # 会话目录 (主数据在 SQLite，备用)
├── logs/               # 结构化日志目录
├── mcp_servers.yaml    # MCP Server 配置
├── oryxos.db           # SQLite 数据库文件
├── AGENTS.md           # Bootstrap：项目级 agent 行为说明
├── SOUL.md             # Bootstrap：agent 人格定义
└── USER.md             # Bootstrap：用户偏好 (只读，agent 不修改)
```

- **`MEMORY.md` vs `USER.md`**：
  - `USER.md`：用户手写的初始设定，OryxOS 只读不写。
  - `MEMORY.md`：Agent 通过 `save_memory` 写入的成长记录，OryxOS 读写。

---

## 6. 核心数据模型与存储

### AGENT.md 格式示例
```markdown
---
name: ops-agent
description: 运维助手
identity:
  agent_name: 运维小欧
  prompt: 你是一个专业的运维助手...
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7
  api_key: ${DEEPSEEK_API_KEY}   # 环境变量占位，严禁硬编码
tools:
  - read_file
  - shell
  - http_get
  - save_memory
  - recall_memory
mcp_servers:
  - github-mcp
channels:
  - name: cli
bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md
settings:
  max_iterations: 10
  max_history_turns: 20
---

你是一个专业的运维助手。被触发时……（Agent 任务指令正文，注入 system prompt）
```

### SQLite 核心审计表
1. **`sessions`**：`session_id` (PK), `profile_name`, `channel`, `user_id`, `messages_json`, `status`, `created_at`, `last_active_at`
2. **`tool_invocations`**：`id` (PK), `session_id`, `tool_name`, `input_json`, `result_json`, `success`, `error_message`, `duration_ms`, `created_at`
3. **`llm_calls`**：`id` (PK), `session_id`, `provider`, `model`, `prompt_tokens`, `completion_tokens`, `total_tokens`, `duration_ms`, `created_at`

---

## 7. ReAct Loop 工作机制

```
用户消息 / 定时触发 (AgentScheduler)
  → 追加到 Session 对话历史
  → PromptBuilder 组装 Prompt：
      [1] system prompt（AGENT.md 正文 + 绑定 Skill 元数据 + Bootstrap）← ContextLoader
      [2] 长期记忆（MEMORY.md 全文 / 分区截断内容）                  ← MemoryService
      [3] 对话历史（最近 max_history_turns 轮）                     ← SessionManager
      [4] 可用 Tool 列表（Function Calling Schema）                 ← ToolRegistry
  → ProviderService 调用 LLM（记录 llm_calls 审计）
  → [无 Tool 调用] → 返回最终响应
  → [有 Tool 调用] → ToolExecutor 执行 Tool
      → Sandbox 校验（白名单与防穿越）
      → 执行（内置 Tool 进程内 / MCP Tool 走 JSON-RPC）
      → 记录 tool_invocations 审计
      → 将工具结果作为 tool 消息追加到对话历史
  → 回到组装 Prompt 继续循环（最多 max_iterations 次，默认 10）
```

---

## 8. 内置 Tool 体系（9 个核心 Tool）

| Tool | 实现类 | 说明 |
| :--- | :--- | :--- |
| `read_file` | `FileTools` | 读文件，路径白名单与防越界校验 |
| `write_file` | `FileTools` | 写文件，路径白名单校验 |
| `list_dir` | `FileTools` | 列目录，路径白名单校验 |
| `shell` | `ShellTools` | 执行命令，可执行文件白名单 + argv 直传 + 超时控制 |
| `http_get` | `HttpTools` | GET 请求，默认放行 + SSRF 黑名单过滤 |
| `http_post` | `HttpTools` | POST 请求，域名通配符白名单校验 |
| `save_memory` | `MemoryTools` | 追加写入长期记忆（显式指定 core / archival 分区） |
| `recall_memory` | `MemoryTools` | 检索归档记忆（支持关键词与三路加权混合召回） |
| `notify` | `NotifyTools` | 推送通知到全局渠道（Webhook / 飞书 / 企微 / 钉钉 / Email） |

---

## 9. Web Service REST API（核心 10 个端点，前缀 `/api/v1`）

- `POST /sessions` - 创建会话
- `POST /sessions/{id}/messages` - 发送消息并触发 ReAct Loop
- `GET /sessions/{id}` - 查询会话历史与状态
- `DELETE /sessions/{id}` - 归档会话
- `POST /agents/{name}/invoke` - 无状态直接调用 Agent
- `GET /profiles` - 列出已注册 Profile
- `GET /memory` - 查看长期记忆
- `GET /tools` - 查询可用 Tool 列表与 Schema
- `GET /health` - 健康检查
- `GET /info` - 运行状态与 Provider 信息

---

## 10. 5 个 User Story 推进顺序与依赖

开发过程严格按照 5 个 User Story 依赖关系推进：

```
US-1 (对接 LLM Provider) 
    ↓
US-2 (ReAct 循环引擎)
    ↓
┌───────────────┴───────────────┐
↓                               ↓
US-3 (Memory 三层记忆)    US-4 (Plugin Tool 体系)
└───────────────┬───────────────┘
                ↓
US-5 (Web Service API)
```

---

## 11. 常见陷阱与避坑清单

| 常见陷阱 | 严重后果 | 正确做法 |
| :--- | :--- | :--- |
| **Spring AI 自动执行 Tool** | Tool 被重复调用两次 | 禁用 `ChatClient` 自动 Tool 执行，由 `ToolExecutor` 统一接管 |
| **按 Bean 类型扫描 Provider** | 多 Provider 并存时路由混乱 | 维护显式 `Map<String, ChatModel>` 映射 |
| **把 Agent 目录当 Tool 注册** | 破坏 Agent 语义与调用异常 | 归 `ContextLoader`：正文注入 Prompt，子指令/脚本经 `read_file`/`shell` 按需读取 |
| **审计表仅写日志不落库** | 破坏 Enterprise 核心差异化 | `tool_invocations` 与 `llm_calls` 每次调用同步入 SQLite |
| **依赖 Hibernate DDL 自动建表** | SQLite ALTER TABLE 语法受限报错 | 手写标准 SQL 建表脚本管理 DDL |
| **在 ReAct 循环中滥用异步/响应式** | 代码复杂度剧增，丢失堆栈跟踪 | 全程使用同步阻塞模型 + Java 21 Virtual Thread |
| **配置硬编码 API Key** | 严重安全合规漏洞 | 统一采用 `${ENV_VAR}` 占位符注入 |
| **代码格式与质量未过门禁直接提交** | CI 流水线爆红阻断 | 提交前本地必须跑通 `mvn clean verify`（含 Spotless、P3C、Checkstyle、SpotBugs、OWASP） |
