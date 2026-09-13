# Agent Directory Contract

## 文件布局

```text
.oryxos/agents/<name>/
├── AGENT.md
├── REFERENCE.md          # optional
├── skills/*.md           # optional, private to this Agent
└── scripts/*             # optional, trusted code
```

扫描只把 `.oryxos/agents/` 的直接子目录视为候选 Agent。普通文件和缺少 `AGENT.md` 的目录跳过并记录可诊断信息。

## AGENT.md 语法

```markdown
---
name: <must equal directory name>
description: <optional>
identity: ...
provider:
  name: <required>
  model: <optional>
tools: [...]
notify_channels: [...]
schedules: [...]
bootstrap: [...]
settings: ...
---

<required Agent instructions body>
```

第一个 `---` 开始 frontmatter，第二个 `---` 结束 frontmatter。配置区按既有 ProfileLoader 规则解析，包括 `${ENV_VAR}` 占位。正文只在运行时由 ContextLoader 读取；配置区不得进入提示词。

## Java 运行时契约

### AgentLoader

- `Profile deriveProfile(Path agentDir)`：读取并校验单目录，返回完整 Profile；不注册、不调度。
- `List<Profile> scanAndRegister(Path agentsRoot)`：按目录名稳定顺序扫描，每个成功项依次进入 ProfileRegistry 和 AgentScheduler；单个坏目录不产生部分状态，也不阻止其他目录。
- 加载失败使用既有 `OryxException` 与标准错误码；消息包含目录或缺失字段。

### ProfileRegistry

- `void register(Profile profile)`：同步校验并原子写入。
- `void remove(String name)`：原子移除注册项。
- `boolean exists(String name)`：只查询当前注册表，不触发磁盘懒加载。
- 既有 `getProfile`、`getRequiredProfile`、`containsProfile` 保持兼容。

### AgentScheduler

- `void registerProfile(Profile profile)`：同步协调该 Profile 全部 schedules，并为启用项保存句柄。
- `void registerAll()`：遍历 registry，并复用 `registerProfile`。
- harness 可通过包内只读测试缝隙判断句柄存在；不公开可变句柄 Map，也不新增课件之外的公共方法。

## 上下文契约

对于 `.oryxos/agents/<profile.name>/AGENT.md` 存在的目录型 Agent：

- 每次 `ContextLoader.loadContext(profile)` 重读主文件正文。
- 只注入第二个 `---` 后的正文。
- 不读取 `skills/*.md`、`REFERENCE.md`、`scripts/*`。
- Agent 可按正文指引调用现有 `read_file` 或 `shell`；访问仍经过 Sandbox 和工具审计。

对于没有目录主文件的历史 Profile，保留既有 Bootstrap 和 Skill 行为以避免前序功能回归。

## 兼容与边界

- 不新增 REST API、数据库表、Profile 字段或第三方依赖。
- 不提供 Agent 版本、同名冲突策略、文件监听、市场、共享 Skill、容器或子进程网络隔离。
- 安装带脚本的 Agent 等价于信任脚本作者。
