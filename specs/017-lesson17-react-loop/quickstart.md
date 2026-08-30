# Quickstart: 第17节 ReAct 循环核心引擎与 Agent 上下文编排

## 快速验证命令

```bash
# 1. 运行所有单元测试与 5 项验收 Harness
mvn test -pl oryxos-core,oryxos-storage,oryxos-provider

# 2. 全量静态质量门禁检查
mvn clean verify
```

## 5 项验收 Harness 目标

1. `ReActLoopTest` - 验证单轮直接结束、多轮工具调度执行、转满最大轮数（10轮）强制停。
2. `PromptBuilderTest` - 验证四大板块顺序拼装、历史超 20 轮截断、System Prompt 末尾注入当前日期时间。
3. `ToolExecutorTest` - 验证成功记录 `success=true`、失败记录 `success=false` 与 `error_message`，异常不吞。
4. `AgentServiceTest` - 验证在 `process` 期间 `ProfileContext` 可用，抛异常时 `finally` 必定清理 `ProfileContext`，会话成功保存。
5. `ContextLoaderTest` - 验证动态实时读取无缓存、Skill 缺失报错、Bootstrap 缺失告警。
