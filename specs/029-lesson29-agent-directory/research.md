# Research: 一个目录定义一个会自己运行的 Agent

## 决策 1：复用 Profile，不建立第二套运行模型

**Decision**: `AgentLoader.deriveProfile(Path)` 把 `AGENT.md` frontmatter 委托给既有 `ProfileLoader` 映射为 `Profile`，随后进入既有注册、调度和执行链。

**Rationale**: 第 16～28 节的 Provider、ReAct、Tool、Memory、Session、Notify 和 Scheduler 都以 `Profile` 为输入。复用该值对象能保持审计、错误处理和会话身份不变。

**Alternatives considered**: 新建独立 Agent 运行实体会复制配置字段并迫使底座增加分支；直接把 Agent 目录注册成 Tool 会混淆“执行主体”和“基础能力”。

## 决策 2：Agent 私有资源覆盖旧公共 Skill 模型

**Decision**: `skills/*.md`、`REFERENCE.md`、`scripts/*` 只属于所在 Agent；不建立 `.oryxos/skills` 索引，不创建软链接，不新增 `use_skill` 工具。

**Rationale**: 用户明确要求以修改后的第 29 节为准，课件把 Agent 定义为一个自足目录，并明确取消跨 Agent 能力复用。

**Alternatives considered**: 公共 Skill + Agent 软链接是旧技术方案，已被本次用户裁决覆盖；双轨兼容会让新 Agent 的资源披露规则含糊。

## 决策 3：目录型 Agent 通过约定路径重读正文

**Decision**: `ContextLoaderImpl` 使用 `profile.name` 定位 `.oryxos/agents/<name>/AGENT.md`，每轮读取并剥离 frontmatter，不向 `Profile` 新增源路径字段。

**Rationale**: 目录名必须等于 Agent 名，约定路径足以稳定定位主文件，并满足无缓存热生效；同时避免本节交付清单之外的 Profile 字段。

**Alternatives considered**: 把正文缓存在 `identity.prompt` 会失去即时生效；给 `Profile` 增加 `agentDirectory` 会扩大公共配置模型；把 AGENT.md 塞入 Bootstrap 会混淆主指令与项目 Bootstrap。

## 决策 4：目录加载采用事务式顺序与单目录故障隔离

**Decision**: 每个目录先完成读取、解析、字段校验、目录名校验和工具告警，再写入注册表和调度器；一个目录失败时记录路径和原因并继续其他目录。

**Rationale**: 先校验后写入防止半注册状态，逐目录隔离保持启动可用，也与现有 ProfileLoader 的容错方式一致。

**Alternatives considered**: 整批全有或全无会让一个坏 Agent 阻止所有合法 Agent 启动；先注册后校验会留下不可执行的 Profile。

## 决策 5：注册中心承担共同运行时校验

**Decision**: `ProfileRegistry.register` 统一校验必填字段，并在 Spring 运行时结合已有 `ProviderService` 与 `OryxTool` 集合检查 Provider、告警未知工具；文件路径加载继续复用同样消息。

**Rationale**: 启动扫描和第 30 节运行时 API 最终都会调用注册中心，将共同规则放在写入口可避免绕过。

**Alternatives considered**: 仅在 AgentLoader 校验会让直接运行时 `register(Profile)` 绕过规则；在 Controller 校验会重复且第 29 节没有新增 API。

## 决策 6：单 Agent 调度注册成为批量注册的唯一循环体

**Decision**: 从 `registerAll` 提取 `registerProfile(Profile)`；每个启用的 schedule 保存 `ScheduledFuture<?>`，重复注册先替换旧句柄。

**Rationale**: 启动批量加载和运行时新增需要完全相同的 cron、时区、状态协调与句柄行为。

**Alternatives considered**: AgentLoader 自己构造 CronTrigger 会复制第 25/28 节逻辑；批量和单个入口各自维护循环会继续产生行为漂移。

## 决策 7：脚本执行沿用现有沙箱并补足参数路径约束

**Decision**: 保留命令精确白名单与 argv 直传；对于 `python`/`python3`/`bash` 等解释器，要求第一个脚本参数是 `.oryxos/agents/<name>/scripts/` 下的规范化文件，并拒绝命令字符串执行选项。配置仅在现有 `shell.allowed-commands` 中增加需要的解释器值。

**Rationale**: 当前 Sandbox 只验证可执行文件名，单独允许解释器会放大任意代码入口。课件要求“解释器 + Agent scripts 目录”两层限制，且明确不承诺子进程网络隔离。

**Alternatives considered**: 新增容器或网络命名空间超出核心阶段；新增脚本专用 Tool 与课件“无新工具”冲突；完全不放行解释器会让示例 Agent 无法运行。

## 决策 8：不新增依赖与存储

**Decision**: 使用现有 SnakeYAML 2.3、Spring 6.1.14、Java NIO 与并发集合；不新增表、迁移或第三方库。

**Rationale**: Maven 依赖树已确认所需解析和装配能力存在；Agent 定义以工作区文件为真源。

**Alternatives considered**: 文件监听、数据库化 Agent 元数据和插件框架均属于第 30 节或扩展阶段。
