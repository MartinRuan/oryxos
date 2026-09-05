# Implementation Plan: 第19节 Notify 模块 原理解析、实现与代码讲解

**Branch**: `019-lesson19-notify` | **Date**: 2026-08-30 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/019-lesson19-notify/spec.md)

**Input**: Feature specification from `/specs/019-lesson19-notify/spec.md`

## Summary

实现 OryxOS 的出站通知子系统（Notify 模块），补齐 Agent 运行时在定时调度和自主触发场景下的主动推送能力。遵循"接口先行"原则，定义中立的 `NotifyChannelAdapter` 接口与 `NotifyTarget` 模型，提供通用 HTTP `WebhookNotifyAdapter` 实现；实现内置 `NotifyTools`，支持从 `ProfileContext` 解析渠道配置并在发送前强制执行 `Sandbox.enforce` 白名单校验。

## Technical Context

- **Language/Version**: Java 21 (LTS) with Virtual Threads enabled
- **Fixed Tech Stack**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **Module Allocation**: `notify 包（Adapter/Target/Webhook 实现/NotifyTools）→oryxos-tool；Profile 与 ProfileLoader notify_channels 结构支持、ProfileContext 上下文解析→oryxos-core`
- **Testing Strategy**: `测试策略按课件"验收 harness"执行：WebhookNotifyAdapterTest、NotifyToolsTest（覆盖 发送前必须先过白名单校验、未配置渠道报错、缺省取首个渠道、5xx异常不吞），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **Syntax Forbidden**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

- [x] **原则一：自实现 ReAct Loop**（Notify 作为内置 Tool 被调度，不改变自研 Loop 机制）
- [x] **原则二：Spring AI 职责限定**（`@Tool` 仅用于元数据/Schema，工具执行由 ReActLoop + ToolExecutor 统一调度）
- [x] **原则三：Provider 显式映射**（不影响 Provider 路由映射）
- [x] **原则四：一个目录 = 一个 Agent & 渐进式披露**（通知渠道配置定义在 Profile 中，凭证与 URL 不进入对话提示词）
- [x] **原则五：审计表 Day One 写入**（`notify` 作为 Tool 执行，其成功/失败与耗时通过既有 `tool_invocations` 统一落库审计）
- [x] **原则六：应用层白名单沙箱**（出站网络请求必须在发送前调用 `Sandbox.enforce(HTTP_REQUEST, url)` 校验）
- [x] **原则七：同步阻塞执行模型**（基于 RestClient / 同步阻塞 HTTP 客户端，由虚拟线程承载高并发）
- [x] **原则八：Tool 模块三合一**（内置 Tool、MCP、Sandbox Check 同属 `oryxos-tool`）

## Project Structure & Module Allocation

### Documentation (this feature)

```text
specs/019-lesson19-notify/
├── plan.md              # 本计划文档
├── research.md          # 架构决策与调研分析
├── data-model.md        # NotifyTarget 与 Profile 配置数据模型
├── quickstart.md        # 验证指南与验收测试说明
├── contracts/           # 接口契约定义
│   └── notify-contracts.md
└── tasks.md             # 任务拆解与实施清单
```

### Source Code Allocation

- **oryxos-core**:
  - `com.oryxos.core.model.Profile`（增强 `notifyChannels` 结构化支持 `NotifyChannelConfig` 及 `resolveNotifyChannel`）
  - `com.oryxos.core.profile.ProfileLoader`（解析 `notify_channels`，支持 ${ENV_VAR} 占位符解析）
  - `com.oryxos.core.context.ProfileContext`（增强 Bean 注入支持与 `resolveNotifyChannel` 便捷方法）
- **oryxos-tool**:
  - `com.oryxos.tool.sandbox.ActionType`（沙箱动作枚举：`FILE_READ`, `FILE_WRITE`, `SHELL_COMMAND`, `HTTP_REQUEST`）
  - `com.oryxos.tool.sandbox.SandboxAction`（沙箱动作值对象）
  - `com.oryxos.tool.sandbox.Sandbox`（增加 `enforce(SandboxAction action)` 契约方法）
  - `com.oryxos.tool.notify.NotifyTarget`（通知目标 Record）
  - `com.oryxos.tool.notify.NotifyChannelAdapter`（通知渠道适配器接口）
  - `com.oryxos.tool.notify.impl.WebhookNotifyAdapter`（通用 Webhook POST 适配器实现）
  - `com.oryxos.tool.builtin.NotifyTools`（内置 `notify` 工具实现）
  - `com.oryxos.tool.config.ToolAutoConfiguration`（自动装配相关 Bean，如 RestClient）

### Tests Allocation

- **oryxos-tool**:
  - `com.oryxos.tool.notify.WebhookNotifyAdapterTest`（MockWebServer 本地模拟 Webhook 测试）
  - `com.oryxos.tool.builtin.NotifyToolsTest`（Mockito 顺序断言与异常场景测试）
- **oryxos-core**:
  - `com.oryxos.core.profile.ProfileLoaderTest`（`notify_channels` 字段解析回归测试）

## Test Strategy

- **课件验收 Harness 测试**：
  1. `WebhookNotifyAdapterTest`：
     - 发送 POST 请求至指定 Webhook URL；
     - 载荷包含 JSON 格式 `{"content": "..."}`；
     - URL 动态来源于 `NotifyTarget.config()` 而非硬编码；
     - Webhook 返回 5xx 状态码时异常向上抛出，严禁静默吞掉。
  2. `NotifyToolsTest`：
     - `notify_channels` 未配置时明确抛出异常/报错；
     - `channel` 参数缺省时自动采用首个配置的通知渠道；
     - **顺序断言（InOrder）**：`sandbox.enforce` 必须严格先于 `adapter.send` 被调用。
- **全量构建门禁**：`mvn clean verify` 全绿（Spotless、P3C、Checkstyle、SpotBugs、OWASP）。
