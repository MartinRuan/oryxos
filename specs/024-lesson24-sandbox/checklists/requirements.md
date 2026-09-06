# Requirements Checklist: Lesson 24 Sandbox

- [ ] Sandbox 接口契约中立，仅包含 enforce 方法与意图描述
- [ ] SandboxAction 仅包含 ActionType 与 target，无特定隔离实现参数
- [ ] ActionType 包含 FILE_READ, FILE_WRITE, SHELL_COMMAND, HTTP_REQUEST 四种类型
- [ ] SandboxViolationException 继承 RuntimeException，提示友好清晰
- [ ] FileSandboxProperties, ShellSandboxProperties, HttpSandboxProperties 绑定正确 prefix
- [ ] WhitelistSandbox 严格实现路径防穿越（Path.normalize().toAbsolutePath()）
- [ ] WhitelistSandbox 严格实现 Shell 命令首 token 提取
- [ ] WhitelistSandbox 严格实现通配符域名点号边界匹配（防 evil-example.com 伪装）
- [ ] WhitelistSandbox 在配置为空时遵循全闭原则（拒绝所有请求）
- [ ] ToolAutoConfiguration 注册 WhitelistSandbox 并注入配置属性
- [ ] 现有 FileTools, ShellTools, HttpTools, NotifyTools 确认调用 sandbox.enforce 且被拦截时不触发真实 IO
- [ ] WhitelistSandboxTest 单元测试覆盖所有典型安全场景与边界用例
