# Tasks: 第19节 Notify 模块 原理解析、实现与代码讲解

**Branch**: `019-lesson19-notify` | **Date**: 2026-08-30 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/019-lesson19-notify/spec.md) | **Plan**: [plan.md](file:///e:/study/aiprogram/oryxos/specs/019-lesson19-notify/plan.md)

## Phase 1: Context & Profile Configuration Support (oryxos-core)

- [x] **Task 1.1**: 在 `oryxos-core` 扩展 `Profile` 模型与 `ProfileContext`，支持结构化 `notifyChannels` (`NotifyChannelConfig`) 及 `resolveNotifyChannel(name)` 渠道解析方法。
- [x] **Task 1.2**: 在 `oryxos-core` 增强 `ProfileLoader`，支持 YAML / AGENT.md 中 `notify_channels` 字段解析及 `${ENV_VAR}` 占位符解析，并在 `ProfileLoaderTest` 中增加针对性单测。

## Phase 2: Sandbox Contract & Notify Abstractions (oryxos-tool)

- [x] **Task 2.1**: 在 `oryxos-tool` 定义 `ActionType` 枚举（`FILE_READ`, `FILE_WRITE`, `SHELL_COMMAND`, `HTTP_REQUEST`）与 `SandboxAction` Record，在 `Sandbox` 接口中增加 `enforce(SandboxAction action)` 契约方法。
- [x] **Task 2.2**: 在 `oryxos-tool` 定义 `NotifyTarget` Record 与 `NotifyChannelAdapter` 接口契约。

## Phase 3: WebhookNotifyAdapter & Test Harness (oryxos-tool)

- [x] **Task 3.1 (TDD)**: 编写 `WebhookNotifyAdapterTest` 测试类，使用本地模拟服务验证：
  - 发送 POST 请求至指定 Webhook URL；
  - 载荷包含 JSON 格式 `{"content": "..."}`；
  - URL 动态取自 `NotifyTarget.config()`；
  - 远端 5xx 状态码或网络异常如实上抛。
- [x] **Task 3.2**: 实现 `WebhookNotifyAdapter`（基于 Spring `RestClient` 发送 HTTP POST 请求）。

## Phase 4: NotifyTools & Sandbox Sequence Harness (oryxos-tool)

- [x] **Task 4.1 (TDD)**: 编写 `NotifyToolsTest` 验收测试类，mock `Sandbox` 与 `NotifyChannelAdapter`，覆盖核心守点：
  - `notify_channels` 未配置时抛出明确异常；
  - `channel` 参数缺省时自动使用首个配置的通知渠道；
  - **白名单校验先行**：使用 Mockito `InOrder` 断言 `sandbox.enforce(new SandboxAction(HTTP_REQUEST, url))` 严格先于 `adapter.send(target, content)` 被调用。
- [x] **Task 4.2**: 实现 `NotifyTools` 内置工具（标注 `@Tool` 注解，执行 Profile 渠道解析、沙箱前置校验与适配器转发）。

## Phase 5: Verification & Quality Gates

- [x] **Task 5.1**: 运行 `mvn test -pl oryxos-tool` 与 `mvn test -pl oryxos-core`，确保所有单元测试全部通过。
- [x] **Task 5.2**: 执行全库质量门禁 `mvn clean verify`（通过 Spotless、P3C、Checkstyle、SpotBugs、OWASP 检查）。
- [x] **Task 5.3**: 交付物逐项核对与第19节验收报告生成。
