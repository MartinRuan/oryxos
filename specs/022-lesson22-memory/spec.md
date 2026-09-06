# Feature Specification: 第22节 Memory 实现与代码讲解

**Feature Branch**: `022-lesson22-memory`

**Created**: 2026-09-05

**Status**: Draft

**Input**: 第22节需求：Memory 模块——三层记忆门面、分区长期记忆存储与 Agent 读写工具

## 背景与价值

Agent OS 区别于普通聊天机器人的核心能力之一是跨会话记忆能力。在此之前，Agent 具备推理能力（ReAct 循环）与行动能力（Tool 体系），但每次对话历史随着上下文窗口关闭即清空，无法形成对用户习惯与关键事实的持久化积累。

本模块构建统一的记忆管理门面 `MemoryService`，底层通过单文件两分区（`## 核心记忆` 与 `## 归档记忆`）管理长期记忆文件 `MEMORY.md`。同时暴露内置工具 `save_memory` 与 `recall_memory` 赋予 Agent 主动记录与检索记忆的能力。长期记忆实行**零内存缓存（Zero Caching）**保障写后立读，在超出字符阈值（4000字）时实行**严格分区隔离截断**（绝对不裁切核心记忆区），并在组装 Prompt 时将核心记忆无缝注入，为跨会话偏好保持（如每日科技日报定制）提供确定性基础设施。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 长期记忆分区持久化与截断安全隔离 (Priority: P1)

作为 Agent 底座引擎，需要将长期记忆持久化存储在分区的 `MEMORY.md` 文件中，支持按核心区与归档区分区追加；在长期记忆超出字符阈值时执行安全截断，保证核心记忆一字不丢，归档记忆保留最新内容，且每次读写直接操作物理文件不使用内存缓存。

**Why this priority**: 核心记忆区是 Agent 感知用户根本偏好与事实的基石（"始终在场"），如果截断逻辑粗暴截断全文件会导致核心设定丢失；而缓存会导致写入后同一进程下一轮迭代无法立即可见。

**Independent Test**: 实例化长期记忆组件，向核心区写入用户身份偏好，再向归档区灌入远超 4000 字符的日志流，执行加载，断言核心记忆完全保留，归档区头部旧记录被裁掉、尾部新记录保留；写入后立即调用加载与关键词检索断言直接命中。

**Acceptance Scenarios**:
1. **Given** 长期记忆文件包含 `## 核心记忆` 与 `## 归档记忆` 两分区，**When** 写入核心记忆与大量归档记忆直到归档区超过 4000 字符，**Then** 加载内容中核心记忆内容原样完整保留，归档区仅保留最新 4000 字符。
2. **Given** 长期记忆已写入新条目，**When** 同一进程立即调用 `load()` 或 `recallByKeyword()`，**Then** 无需等待或手动刷新缓存，立即可读取到刚写入的新条目。
3. **Given** 待写入条目与指定目标分区（`CORE` 或 `ARCHIVAL`），**When** 执行追加写入，**Then** 条目按日期标签格式（`\n- [YYYY-MM-DD] 内容`）准确追加到对应二级标题区块下方。

---

### User Story 2 - Agent 主动读写内置工具集成 (Priority: P2)

作为 Agent，在执行任务时能自主识别值得长期保存的偏好或事实，调用 `save_memory` 显式写入核心或归档记忆；在需要回顾历史上下文时，调用 `recall_memory` 按关键词检索归档记忆并获得结果或友好提示。

**Why this priority**: 核心阶段遵循"写入靠 Agent 主动调用、不做自动提炼"原则，工具是 Agent 触达长期记忆的唯一交互通道。

**Independent Test**: 注册 `MemoryTools`，调用 `save_memory(content, scope)` 验证入参路由与默认值（scope 缺省自动路由至 archival）；调用 `recall_memory(keyword)` 验证大小写不敏感行匹配，检索未命中时返回友好提示"没有找到相关记忆"而不抛出异常。

**Acceptance Scenarios**:
1. **Given** Agent 调用 `save_memory` 仅传入待记录内容未传入 `scope`，**When** 工具执行写入，**Then** 默认安全路由写入归档记忆区，返回"已记住"。
2. **Given** Agent 调用 `save_memory` 显式传入 `scope="core"`，**When** 工具执行写入，**Then** 条目正确写入核心记忆区。
3. **Given** 归档记忆中包含相关关键词条目，**When** Agent 调用 `recall_memory`（支持不区分大小写匹配），**Then** 返回匹配到的条目列表换行拼接文本。
4. **Given** 归档记忆中不包含查询关键词，**When** Agent 调用 `recall_memory`，**Then** 返回"没有找到相关记忆"，不抛出异常。

---

### User Story 3 - 统一记忆门面与 Prompt 结构化注入 (Priority: P3)

作为 ReAct 循环与 PromptBuilder，通过统一的 `MemoryService` 门面接口组装上下文，将核心记忆自动拼装进 System Prompt 中；归档记忆不整体盲目注入，仅在 Agent 需要时按需检索。

**Why this priority**: 建立清晰的"接口墙"，上层（PromptBuilder / ReActLoop）全程面向 `MemoryService` 交互，不直接耦合底层文件 IO 或会话实现，保证后续平滑演进。

**Independent Test**: Mock `Session` 与核心记忆内容，调用 `memoryService.buildContext(session)`，断言返回结果仅包含核心记忆与会话历史必要摘要，绝不整体注入海量归档记忆；调用 `PromptBuilder.build` 验证注入的 System Prompt 包含核心记忆。

**Acceptance Scenarios**:
1. **Given** 包含核心记忆条目与归档条目的存储状态，**When** `MemoryService.buildContext(session)` 被调用，**Then** 返回核心记忆内容，归档记忆区内容不被整体注入。
2. **Given** 启用了 Memory 模块的 Agent 会话，**When** `PromptBuilder.build()` 执行组装，**Then** 核心记忆无缝融入 System Prompt 第二段，位于 Bootstrap/Skill 之后、当前日期时间之前。

---

## 边界与明确不做 (Explicit Out of Scope)

1. **不做自动记忆抽取与提炼**：对话过程中不通过后台 LLM 异步或同步总结提炼记忆，何时记、记到哪里完全由 Agent 在 ReAct 循环中显式调用 `save_memory` 决定。
2. **不做向量检索与 Embedding 管道**：核心阶段不引入向量数据库（LanceDB/pgvector 等）与 Embedding 模型依赖，长期记忆检索统一采用简单行级关键词包含匹配。
3. **不做情景记忆（Episodic Memory）**：仅实现会话记忆（SQLite）与长期语义事实记忆（`MEMORY.md`）。
4. **不做 Memory Wiki 结构化矛盾检测**：不进行 Claim/Evidence 实体抽取与事实版本消歧，写入直接按追加模式进行。
5. **不做递归记忆压缩**：超出限制时采用截断策略，不做 LLM 递归重写压缩。

---

## 功能需求清单 (Functional Requirements)

- **FR-001**: 长期记忆必须单文件承载在 `.oryxos/memory/MEMORY.md`，通过 `## 核心记忆` 与 `## 归档记忆` 两个二级标题明确物理分区。
- **FR-002**: 长期记忆必须实现**零内存缓存**：每次读取与写入均直接操作底层物理文件，禁止在 Java 堆内维护缓存副本，保证写入立即对后续调用可见。
- **FR-003**: 归档记忆区字符截断阈值定义为 4000 字符（`MAX_ARCHIVE_CHARS = 4000`）。截断时仅从归档区头部丢弃超长字符、保留最新内容；核心记忆区永远保持全量，严禁被截断。
- **FR-004**: 记忆写入支持通过 `MemoryScope` 枚举（`CORE`, `ARCHIVAL`）进行分区路由；工具层缺省路由为 `ARCHIVAL`。条目写入格式统一为 `\n- [YYYY-MM-DD] 内容`。
- **FR-005**: 长期记忆检索仅对归档区执行按行文本包含匹配（不区分大小写）；核心区由于全量注入 Prompt，不参与检索。检索无匹配时返回友好提示"没有找到相关记忆"。
- **FR-006**: 统一门面 `MemoryService` 必须提供三项契约：`buildContext(Session session)`、`remember(String content, MemoryScope scope)`、`recall(String keyword)`。
- **FR-007**: `PromptBuilder` 在组装提示词时必须集成 `MemoryService`，将核心记忆部分拼装进 System Prompt。

---

## 验收标准与测试套件 (Acceptance Criteria)

可自动化部分由课件"验收 harness"测试套件承载，`mvn test` 全绿即通过：
- **`LongTermMemoryTest`**:
  - `截断只裁归档区_核心记忆一字不能少`（回归关键守点：防误删核心记忆）
  - `写入后立刻可读_不允许有缓存`（回归关键守点：防手滑加缓存）
  - `scope路由到正确区块_缺省写入归档区`
  - `recallByKeyword只搜归档区_不区分大小写`
- **`MemoryToolsTest`**:
  - `scope缺省写归档`
  - `关键词未命中返回友好提示不报错`
- **`MemoryServiceTest`**:
  - `buildContext返回核心记忆与会话历史的组合_归档区不整体注入`

**人工验收项**:
- 真模型对话验证：在对话中声明偏好，Agent 主动调用 `save_memory`；开启新会话验证 System Prompt 自动附带该核心记忆。
- 跨进程验证：进程重启后 `MEMORY.md` 记忆完好存在。
- 权限隔离验证：核对代码确认 `USER.md` 全程只读、`MEMORY.md` 可被 Agent 写入。

---

## 边界与异常分支 (Edge Cases)

1. **`MEMORY.md` 文件不存在**：初次读取或写入时若文件不存在，系统应自动创建包含两级标题骨架的默认文件，不得抛出 `FileNotFoundException`。
2. **`scope` 参数非法或大小写不一致**：Agent 传入 "CORE"、"core"、"Archival" 均应正常标准化；若传入非法字符串或 null，应缺省降级为 `ARCHIVAL`，避免调用崩溃。
3. **记忆内容包含换行或特殊字符**：写入时应进行单行或标准 Markdown 列表规整，防止破坏两级标题分区结构。
4. **归档区超阈值但核心区为空**：截断逻辑正常工作，不发生空指针异常或越界。
