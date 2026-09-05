# Quickstart & Verification Guide: 第19节 Notify 模块

## 1. 验证目标

通过单元测试验证 Notify 模块在单机环境下的各项核心行为：
1. `WebhookNotifyAdapter` 能够正确构造 HTTP POST 请求并发送 JSON 载荷；
2. 远端 5xx 故障时异常如实上抛；
3. `NotifyTools` 调用能够正确解析 Profile 中的渠道配置；
4. 沙箱白名单校验（`Sandbox.enforce`）严格先于网络发送调用执行；
5. 未配置渠道时明确报错。

---

## 2. 自动化测试命令

```bash
# 执行 oryxos-tool 模块测试
mvn test -pl oryxos-tool

# 执行全模块代码规范与质量门禁检查
mvn clean verify
```

---

## 3. 人工验证场景

在真实环境中配置群机器人 Webhook：
```yaml
# 在 AGENT.md 中配置
notify_channels:
  - name: test-group
    type: webhook
    url: https://oapi.feishu.cn/open-apis/bot/v2/hook/xxxxxx
```
启动 Agent 并发起消息推送，确认企业 IM 群实时收到格式正确的推送内容。
