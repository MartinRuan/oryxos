# Research: 动态管理 Agent

## Decision 1: Agent 目录保持唯一真相源

**Decision**: 所有创建和更新先形成 `.oryxos/agents/<name>/AGENT.md`，随后调用 `AgentLifecycleService.register(Path)`；启动扫描和 WorkspaceWatcher 也调用同一方法。`ProfileRegistry` 与 `AgentScheduler` 只是由目录派生的运行时投影。

**Rationale**: 这延续第 29 节“一个目录 = 一个 Agent”，避免 API 数据与手工文件出现两个权威来源。

**Alternatives considered**: 直接由 API 写 ProfileRegistry；另建数据库保存 Agent 定义。两者都会造成运行时或数据库与目录漂移。

## Decision 2: AgentStore 使用原子写与可逆更新

**Decision**: `AgentStore` 严格校验 Agent 名称和根路径；创建先写同目录临时文件，再以原子移动替换 `AGENT.md`（文件系统不支持时使用同目录普通替换）。更新前保留原始内容，注册失败时恢复原文件并重新注册原定义。创建失败则注销可能已产生的运行时状态并删除本次目录。

**Rationale**: WatchService 可能观察到中间文件状态，原子替换可缩短不完整窗口；可逆更新才能满足失败后继续使用原 Agent 的要求。

**Alternatives considered**: 直接覆盖目标文件；只回滚内存注册。前者会暴露半写内容，后者会让目录与运行时不一致。

## Decision 3: 归档整个目录并生成唯一名称

**Decision**: 删除把完整目录移动到 `.oryxos/archive/<name>-<UTC时间戳>/`；若同毫秒冲突则追加短随机后缀。归档保留原始目录内容，不改审计表。

**Rationale**: 同名 Agent 可重复创建和删除，唯一归档名既避免覆盖又保留追溯顺序。

**Alternatives considered**: 物理删除；固定 `.oryxos/archive/<name>/`。前者破坏可追溯性，后者会覆盖历史归档。

## Decision 4: WatchService 监听根目录与 Agent 子目录

**Decision**: WorkspaceWatcher 启动时同步全量扫描；随后由一个可关闭的守护线程持有 JDK WatchService，同时注册 `agents/` 根目录和每个 Agent 子目录。根目录 create/delete 对应注册或注销，子目录内 `AGENT.md` 的 create/modify/delete 对应重新注册或注销。单事件失败记录后继续循环。

**Rationale**: 只监听根目录无法收到既有 Agent 内 `AGENT.md` 的内容修改；双层监听覆盖课件要求且无需第三方库。

**Alternatives considered**: 定时轮询；递归监听整个工作区。轮询延迟和无效 I/O 更高，监听整个工作区扩大了事件噪声与安全边界。

## Decision 5: API 与 Watcher 重复事件按 Agent 名称串行且幂等

**Decision**: AgentLifecycleService 对同一 Agent 名称串行执行 create/register/update/delete；`register(Path)` 可重复调用，注册前派生完整 Profile，ProfileRegistry 替换同名投影，AgentScheduler.registerProfile 先取消旧句柄再协调新任务。Watcher 对 API 原子写产生的事件再次调用 register 不产生重复定时任务。

**Rationale**: API 写文件必然可能触发 WatchService；把重复事件设计成幂等比尝试识别事件来源更可靠。

**Alternatives considered**: API 写入期间暂停监听；给事件附加来源标志。两者都有竞态窗口并增加状态管理。

## Decision 6: 生成 Provider 使用专用显式配置

**Decision**: 新增 `oryxos.agent-generation.provider` 与 `oryxos.agent-generation.model`，默认 `minimax` / `MiniMax-M2.7`。AgentLifecycleService 构造不注册的生成 Profile，使用 SessionManager 创建生成会话，再调用既有 ProviderService 文本入口。

**Rationale**: 用户在 clarify 阶段选择方案 A；显式配置避免从无序 Provider 集合猜测默认项，并保持 Provider 显式映射原则。ProviderService 已负责成功/失败审计。

**Alternatives considered**: 请求每次携带 provider/model；复用名为 `minimax-agent` 的业务 Agent；选择第一个可用 Provider。它们分别增加调用复杂度、造成业务耦合或产生不确定行为。

## Decision 7: 生成内容以内存方式复用 AgentLoader 校验

**Decision**: 将 AgentLoader 的文档拆分与 Profile 派生逻辑提取为同包可复用入口，`deriveProfile(Path)` 和生成校验共同调用；生成流程不创建临时 Agent 目录。只有合法草稿才返回。

**Rationale**: 这样能证明生成草稿可由现有规则解析，同时严格满足“不落盘、不注册”。

**Alternatives considered**: 在临时目录写入后调用 deriveProfile；只用正则检查 frontmatter。前者违反生成无文件副作用，后者会产生两套校验。

## Decision 8: 工作区读取使用规范化与真实路径双重边界

**Decision**: 接口只接受相对 `.oryxos/` 的路径。先 `normalize()` 并检查解析路径位于根目录，再对存在目标执行 `toRealPath()` 并再次检查真实路径位于根目录；只返回普通 UTF-8 文本文件，拒绝目录、二进制、越界与符号链接逃逸。

**Rationale**: 单纯字符串或 normalize 检查挡不住工作区内指向外部的符号链接，真实路径复核符合宪章的文件沙箱要求。

**Alternatives considered**: 仅禁止 `..`；仅检查扩展名。两者均无法防御所有路径和符号链接绕过。

## Decision 9: REST DTO 留在 Web，核心服务使用最小参数与安全视图

**Decision**: Create/Update/Generate 请求 DTO 与 FileNode 位于 `oryxos-web`；AgentLifecycleService 接收名称、完整/合成后的 AGENT.md 文本及生成句子，返回不含 API key 的 AgentView。结构化请求到 AGENT.md 的映射在 Web 边界完成，最终仍由核心注册链校验。

**Rationale**: 避免 oryxos-core 依赖 Web 类型，并保持跨模块依赖方向。安全视图防止 ProfileLoader 展开的环境变量密钥出现在响应。

**Alternatives considered**: 核心服务直接依赖 REST DTO；直接序列化 Profile。前者破坏模块边界，后者可能泄露已解析的 API key。

## Decision 10: 管理平台继续使用原生前端

**Decision**: 在现有 `static/admin/` 的 HTML、CSS、JavaScript 中增加 Agent 管理与工作区面板，复用统一 request/envelope 处理；删除使用浏览器内二次确认，生成结果进入可编辑文本区后再创建。

**Rationale**: 第 26 节已经建立无构建步骤的静态管理端，本节无需引入前端依赖或新工程。

**Alternatives considered**: 引入 React/Vue 或单独前端构建。超出本节依赖与打包边界。

## Decision 11: 单文件记忆使用 Agent 标记保持向后兼容

**Decision**: 继续使用 `.oryxos/memory/MEMORY.md` 的核心/归档双分区。Agent 在 `ProfileContext` 内调用 `save_memory` 时，新条目写成 `- [日期] [agent:<name>] 内容`；Prompt 注入、`recall_memory` 和 `GET /api/v1/memory?agent=<name>` 仅返回该 Agent 的标记条目与无标记历史条目，并在返回内容中隐藏标记。无 Profile 上下文和无查询参数的既有调用保持全量行为。

**Rationale**: 该方案不迁移现有文件、不新增数据库表，也不要求用户手工处理历史数据；新数据具备 Agent 隔离，历史数据继续按原来的共享语义生效。

**Alternatives considered**: 为每个 Agent 新建独立 MEMORY.md；把记忆迁移到数据库；把全局记忆原样展示给每个 Agent。前两项改变存储拓扑或引入迁移，后一项不能形成真实关联。
