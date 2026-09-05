# Feature Specification: 019-lesson19-notify

**Feature Branch**: `019-lesson19-notify`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "第19节需求：Notify 模块——出站通知与主动推送能力。让 Agent 在定时或自主运行场景下具备将处理结果主动送出到外部渠道（如企业 IM 群 Webhook）的标准能力。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 定时与自主场景下的主动通知推送 (Priority: P1)

当 Agent 被定时调度（例如每日早间天气播报、每日科技日报汇总）或无前台会话交互触发时，Agent 需要在 ReAct 循环完成后通过统一的通知机制将生成的内容推送给指定的企业通知渠道（如飞书、企业微信、钉钉的群机器人 Webhook），让群成员及时接收到消息。

**Why this priority**: 核心入站渠道（CLI/Web）仅支持同步请求-响应；出站主动推送是定时调度和全自主 Agent 闭环所不可或缺的对称能力。

**Independent Test**: 可通过独立调用通知发送接口，使用本地 Mock 服务验证是否构造出符合协议的 POST 请求，且内容正确送达。

**Acceptance Scenarios**:
1. **Given** 目标通知渠道配置了有效的 Webhook URL，**When** 发送指定文本内容，**Then** 向该 URL 发起 HTTP POST 请求并附带包含消息内容的 JSON 载荷。
2. **Given** 远端 Webhook 服务返回 5xx 错误或连接超时，**When** 执行发送动作，**Then** 异常必须如实向上抛出，严禁静默吞掉失败。

---

### User Story 2 - Agent 内置 Notify 工具调用 (Priority: P2)

Agent 运行时在需要对外推送消息时，可直接调用内置的 `notify` 工具。该工具接收推送内容 `content` 和可选的渠道名称 `channel`，自动从当前 Agent 的 Profile 配置中解析出对应的渠道配置并执行安全校验与发送。

**Why this priority**: 统一 Agent 工具层调用契约，避免各业务 Agent 重复编写定制 HTTP POST 逻辑。

**Independent Test**: Mock 沙箱白名单与通知适配器，传入内容及渠道参数，验证工具执行流程与返回的 `ToolResult`。

**Acceptance Scenarios**:
1. **Given** 当前 Agent 的 Profile 未配置任何通知渠道，**When** 调用 `notify` 工具，**Then** 返回明确的失败提示或抛出异常，不允许静默假装成功。
2. **Given** 调用 `notify` 工具时未指定 `channel` 参数，**When** Profile 配置了至少一个通知渠道，**Then** 默认使用首个配置的通知渠道完成发送。
3. **Given** 调用 `notify` 工具，**When** 执行发送流程，**Then** 必须严格保证先执行沙箱白名单校验，校验通过后再调用适配器发送。

---

### Edge Cases

- 当 `notify_channels` 为空或 null 时，调用 `notify` 必须明确报错阻止，避免 Agent 误判为已成功推送。
- 当 `target.config()` 中缺失必需的配置项（如 `"url"` 为空或非法 URI）时，适配器需抛出参数非法或配置异常。
- 当 Webhook 响应非 2xx 状态码时，不得返回伪成功，保证调用方与审计层能感知故障。

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 定义统一中立的出站通知渠道适配器接口（`NotifyChannelAdapter`）与通知目标模型（`NotifyTarget`），接口签名不得携带特定厂商实现细节。
- **FR-002**: 系统 MUST 提供通用的 HTTP Webhook 通知适配器（`WebhookNotifyAdapter`），以 JSON 格式发送 POST 请求至指定 Webhook URL。
- **FR-003**: 系统 MUST 提供内置的 `NotifyTools`，暴露 `notify(content, channel)` 工具供 Agent 调用。
- **FR-004**: 系统 MUST 能够从当前线程的 Profile 上下文动态解析通知渠道配置，支持通过名称查找或缺省取首个配置。
- **FR-005**: 系统 MUST 在执行出站网络请求前，强制调用沙箱机制（`Sandbox.enforce`）对目标 URL 进行白名单校验。
- **FR-006**: 系统 MUST 支持在 Agent Profile 中配置 `notify_channels` 字段（包含渠道类型与具体配置键值对），且配置中的凭证与地址不应暴露于对话历史中。

### Key Entities *(include if feature involves data)*

- **NotifyTarget**: 描述通知渠道目标的值对象，包含渠道类型标识（如 `webhook`）及配置键值映射（如 `url`）。
- **NotifyChannelAdapter**: 出站通知渠道契约，定义向指定 `NotifyTarget` 发送文本内容的接口方法。
- **NotifyTools**: 内置工具组件，整合 `ProfileContext`、`Sandbox` 与 `NotifyChannelAdapter` 提供 Agent 工具调用能力。

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 自动化测试套件（含 `WebhookNotifyAdapterTest` 与 `NotifyToolsTest`）全部通过，覆盖 100% 的关键验收与异常分支。
- **SC-002**: 发送流程中沙箱白名单校验与适配器调用的先后顺序具备严格确定的测试验证（校验必须先于发送）。
- **SC-003**: 渠道未配置或网络故障时的异常场景均具备清晰的错误反馈，零静默失败。

---

## Assumptions

- 核心阶段仅提供通用 HTTP Webhook 适配器实现，企业微信、飞书、钉钉等群机器人均使用其标准 Webhook 地址接入。
- 专用认证方式（如签名加签、AccessToken 刷新）以及 SMTP Email 渠道在扩展阶段按需演进，当前接口契约保持中立兼容。
- 沙箱白名单全面实现在第 24 节交付，当前阶段通过契约占位与单元测试 Mock 进行顺序与入参验证。
