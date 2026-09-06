# Quickstart: Lesson 24 Sandbox

## 1. 配置示例 (`application.yaml`)

```yaml
file:
  allowed-paths:
    - .oryxos
    - /tmp
shell:
  allowed-commands:
    - ls
    - git
    - echo
http:
  allowed-domains:
    - api.openai.com
    - dashscope.aliyuncs.com
    - "*.feishu.cn"
```

## 2. 注入并执行代码示例

```java
@Autowired
private Sandbox sandbox;

public void readFile(String path) {
  // 首行执行安全拦截，违规抛出 SandboxViolationException
  sandbox.enforce(new SandboxAction(ActionType.FILE_READ, path));
  
  // 物理 IO 操作
  Files.readString(Path.of(path));
}
```
