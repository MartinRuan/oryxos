# Quickstart: 第 30 节动态管理 Agent 验证

## Prerequisites

- JDK 21。
- 当前目录为仓库根目录。
- `.oryxos/.env` 中提供真实模型测试所需的 `MINIMAX_API_KEY`；自动化测试使用 mock，不依赖外网。
- 使用临时名称 `lesson30-smoke`，不要操作现有业务 Agent。

## 1. Automated harness

先运行第 30 节关键测试：

```bash
./mvnw -pl oryxos-core,oryxos-web -am test \
  -Dtest=AgentLifecycleServiceTest,WorkspaceWatcherTest,WorkspaceApiControllerTest,AgentApiControllerTest,GenerateTest,AgentSchedulerRegisterTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

预期：

- 创建按写目录 → derive/register → schedule 顺序完成。
- 冲突在写目录前拒绝。
- 注册失败删除新目录且没有残留运行时状态。
- 更新 schedules 先注销旧任务再注册新任务。
- 删除严格按注销 → 移出索引 → 归档执行。
- Watcher 对手工增改删实时生效，坏目录不终止监听。
- generate 返回可解析草稿，且没有文件/注册副作用。
- workspace 路径穿越和符号链接越界被拒绝。
- Agent API 使用统一响应信封，既有 invoke 回归不变。

再运行完整门禁：

```bash
./mvnw clean verify
```

预期：12 个 Reactor 模块全部 `SUCCESS`，测试 0 failure/error/skip，静态与安全门禁全部通过。

Agent 记忆兼容回归：

```bash
./mvnw -pl oryxos-memory,oryxos-web -am \
  -Dtest=LongTermMemoryTest,MemoryServiceTest,MemoryToolsTest,MemoryApiControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：新记忆按当前 Agent 标记；指定 Agent 的加载、Prompt 与检索包含自身及历史共享条目，排除其他 Agent；无参数查询保持全量兼容。

## 2. Start the packaged service

```bash
set -a
source .oryxos/.env
set +a
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar --server.port=18080
```

另开终端验证以下流程。

## 2.1 Query Agent-associated memory

```bash
curl --fail --silent \
  'http://127.0.0.1:18080/api/v1/memory?agent=minimax-agent'
```

响应只包含 `minimax-agent` 的专属记忆和历史无标记共享记忆，且不会暴露内部 `[agent:...]` 标记。

## 3. Generate without side effects

记录生成前列表：

```bash
curl --fail --silent http://127.0.0.1:18080/api/v1/agents
```

生成草稿：

```bash
curl --fail-with-body --silent \
  -H 'Content-Type: application/json' \
  -d '{"sentence":"创建一个只回复当前日期、不使用工具、没有定时任务的 lesson30-smoke Agent"}' \
  http://127.0.0.1:18080/api/v1/agents/generate
```

预期：`data` 是可解析的 `AGENT.md` 文本；生成后 Agent 列表不变，`.oryxos/agents/lesson30-smoke/` 不存在；`llm_calls` 有本次 MiniMax 调用审计。

## 4. Create and invoke immediately

将确认后的草稿写入请求体，或使用结构化创建：

```bash
curl --fail-with-body --silent \
  -H 'Content-Type: application/json' \
  -d '{"name":"lesson30-smoke","description":"第30节冒烟 Agent","instructions":"这是连接测试。收到消息后只回复 LESSON30_OK，不调用工具。","provider":{"name":"minimax","model":"MiniMax-M2.7","temperature":0.1},"tools":[],"mcpServers":[],"notifyChannels":[],"schedules":[]}' \
  http://127.0.0.1:18080/api/v1/agents
```

不重启，立即查询并调用：

```bash
curl --fail --silent http://127.0.0.1:18080/api/v1/agents/lesson30-smoke
curl --fail-with-body --silent \
  -H 'Content-Type: application/json' \
  -d '{"content":"连接测试"}' \
  http://127.0.0.1:18080/api/v1/agents/lesson30-smoke/invoke
```

预期：详情可见，真实调用返回 `LESSON30_OK`，响应和日志不含 API key。

## 5. Update and schedule replacement

给 `lesson30-smoke` 增加一个远期 cron，然后再次改 cron。每次更新后读取 schedules 列表：

```bash
curl --fail --silent http://127.0.0.1:18080/api/v2/schedules
```

预期：同一个 schedule key 只有一个当前句柄；更新后旧 cron 不再出现，新 cron 生效。测试完成前不要使用会在验收期间到点的表达式。

## 6. Manual directory hot reload

在 `.oryxos/agents/` 手工创建另一个最小 Agent 目录 `lesson30-watch/AGENT.md`。保存后轮询：

```bash
curl --fail --silent http://127.0.0.1:18080/api/v1/agents
```

预期：5 秒内出现 `lesson30-watch`，无需重启。修改正文后详情反映新内容；把整个目录移动到 `/tmp` 后，5 秒内从列表消失且相关 schedule 被注销。验证完把目录移回或删除测试目录。

## 7. Workspace browser security

正常读取：

```bash
curl --fail --silent 'http://127.0.0.1:18080/api/v1/workspace/tree'
curl --fail --silent 'http://127.0.0.1:18080/api/v1/workspace/file?path=agents/lesson30-smoke/AGENT.md'
```

穿越请求：

```bash
curl --silent --output /tmp/lesson30-traversal.json --write-out '%{http_code}\n' \
  'http://127.0.0.1:18080/api/v1/workspace/file?path=../../etc/passwd'
```

预期：正常文件返回内容；穿越请求返回 400，响应中没有工作区外文件内容。另以工作区内指向外部文件的软链接复核，同样应返回 400。

## 8. Archive and audit continuity

删除测试 Agent：

```bash
curl --fail-with-body --silent -X DELETE \
  http://127.0.0.1:18080/api/v1/agents/lesson30-smoke
```

预期：

- Agent 立即不可查询或调用。
- 相关 schedule 已注销。
- 完整目录出现在 `.oryxos/archive/lesson30-smoke-*/`。
- 生成和调用产生的既有 `llm_calls`/session 记录仍可查询。

## 9. Admin UI

打开 `http://127.0.0.1:18080/admin/`，走完：

1. Agent 管理 → 一句话生成。
2. 编辑草稿中的 cron/tools → 确认创建。
3. 查看详情与编辑。
4. 工作区展开 Agent 目录并只读查看 `AGENT.md`。
5. 删除并完成二次确认。
6. 工作区展开 archive，确认归档目录可见。

任何失败都应在页面展示统一响应中的 `message`。
