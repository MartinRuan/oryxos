# Data Model: 一个目录定义一个会自己运行的 Agent

## AgentDirectory

文件系统中的安装单元，不新增数据库实体。

| 字段 | 来源 | 约束 |
|---|---|---|
| `directoryName` | 目录名 | 非空，必须等于 frontmatter `name` |
| `agentFile` | `<dir>/AGENT.md` | 必须存在且为普通文件 |
| `skillsDirectory` | `<dir>/skills/` | 可选，只包含当前 Agent 私有子指令 |
| `scriptsDirectory` | `<dir>/scripts/` | 可选，只包含可信管理员安装的脚本 |
| `referenceFile` | `<dir>/REFERENCE.md` | 可选 |

关系：一个 AgentDirectory 派生一个 Runtime Profile；可包含零到多个 Internal Resource。

## AgentDefinition

`AgentLoader` 解析期间使用的内部值，不持久化，不作为跨模块公共模型。

| 字段 | 类型 | 约束 |
|---|---|---|
| `agentDirectory` | `Path` | 规范化后的目录路径 |
| `frontmatter` | `String` | 非空、合法 YAML map |
| `instructions` | `String` | 去除 frontmatter 后的正文，非空 |
| `skillFiles` | `List<Path>` | 仅枚举 `skills/*.md`，不读取内容 |
| `scriptFiles` | `List<Path>` | 仅枚举 `scripts/*`，不执行内容 |
| `referenceFile` | `Optional<Path>` | 存在时必须为普通文件 |

状态：`DISCOVERED → PARSED → VALIDATED → REGISTERED → SCHEDULED`。解析或校验失败进入 `REJECTED`，不得产生注册或调度副作用。

## RuntimeProfile

复用 `com.oryxos.core.model.Profile`，不增加字段。

| 映射 | AGENT.md frontmatter |
|---|---|
| `name`, `description` | 同名字段 |
| `identity` | `identity` |
| `provider` | `provider` |
| `tools` | `tools` |
| `notifyChannels` | `notify_channels` |
| `schedules` | `schedules`，包括 id/cron/timezone/message |
| `bootstrap` | `bootstrap` |
| `settings` | `settings` |

验证规则：name/provider/provider.name 必填；name 必须等于目录名；配置了可用 Provider 集合时 provider 必须存在；未知 tool 记录 WARN。重复名称保持注册表单项，完整冲突策略不在本节定义。

## InternalResource

| 种类 | 路径 | 默认上下文行为 | 访问方式 |
|---|---|---|---|
| 主指令 | `AGENT.md` 正文 | 每轮注入 | ContextLoader 重读 |
| 子指令 | `skills/*.md` | 不预载 | `read_file` |
| 参考 | `REFERENCE.md` | 不预载 | `read_file` |
| 脚本 | `scripts/*` | 代码不预载 | `shell` 执行，输出进入工具结果 |

所有资源属于单个 AgentDirectory，不存在跨 Agent 关系。

## ScheduleHandle

复用 `AgentScheduler` 中的运行时映射。

| 字段 | 类型 | 约束 |
|---|---|---|
| `scheduleId` | `String` | 由既有 ScheduleConfig/Store 协调得到，Map key |
| `profileName` | `String` | 对应注册 Profile |
| `future` | `ScheduledFuture<?> ` | 每个启用任务至多一个活动句柄 |

状态沿用第 28 节：配置协调后可为 enabled/disabled/retired；启用创建句柄，停用或替换取消句柄。
