# Research & Architecture Decisions: 第19节 Notify 模块

## 1. 入站 Channel 与出站 Notify 的对称设计

### 背景
- **入站渠道（ChannelAdapter）**：解决"消息怎么进来、怎么触发 Agent 处理"（例如 CLI 控制台输入、Web Service REST 请求、飞书消息监听）。
- **出站通知（NotifyChannelAdapter）**：解决"Agent 主动执行完毕后，结果怎么主动送出去"。在定时调度（如每天早晨 8:00 执行天气分析或科技日报生成）场景下，没有交互方在同步等待响应，必须通过 Notify 主动推送到指定的外部系统或 IM 群。

### 决策
- 将入站与出站抽象明确分开，不强行合并为一个双向连接接口。
- 同一个物理实体（例如企业微信群）可同时作为入站 Channel，也可作为出站 NotifyTarget。

---

## 2. 接口先行与核心阶段通用 Webhook 策略

### 背景
各大主流企业协作平台（企业微信、飞书、钉钉等）均支持基于 HTTP Webhook 的自定义群机器人推送机制。若核心阶段逐一对接各家专用 API（涉及 AppID、AppSecret 鉴权换取 AccessToken、签名算法等），将极大增加复杂性且造成厂商锁定。

### 决策
- **NotifyChannelAdapter 接口抽象**：仅定义 `void send(NotifyTarget target, String content)`，入参不包含任何厂商特定名词。
- **NotifyTarget 数据模型**：包含 `channelType` 和 `Map<String, String> config`。
- **核心阶段唯一实现：`WebhookNotifyAdapter`**：基于 Spring Framework 6.x 的 `RestClient`，以 `application/json` 方式将 `{"content": content}` 发送至 `config.get("url")`。
- **扩展阶段预留**：未来若需要 SMTP 直连发送邮件，新增 `EmailNotifyAdapter`；若需要官方 SDK 特性，可新增专用 Adapter，调用方和接口签名完全无需改动。

---

## 3. 安全沙箱前置校验与执行顺序

### 背景
Notify 发起的是对外 HTTP 请求，必须遵守 OryxOS 的安全边界，不能因为是系统出站推送而绕过沙箱白名单。

### 决策
- 在 `NotifyTools.notify(...)` 执行时，调用顺序必须为：
  1. `ProfileContext.resolveNotifyChannel(channel)` 解析目标渠道；
  2. `sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, url))` 执行白名单校验；
  3. `adapter.send(target, content)` 执行真实网络推送。
- 在单元测试中通过 Mockito 的 `inOrder(sandbox, adapter)` 进行严格的调用顺序断言，确保白名单校验绝对先行。

---

## 4. 依赖库与客户端选型

### 决策
- 使用 Spring Boot 3.3.x 自带的 `org.springframework.web.client.RestClient` 作为同步 HTTP 客户端。
- 单测使用 `okhttp3.mockwebserver.MockWebServer`（若可用）或内置轻量测试方案进行 HTTP 交互验证，测试完全处于离线状态，不产生外部真实网络依赖。
