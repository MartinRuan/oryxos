# Sandbox Contracts

## 1. Java API Contracts

### 1.1 `com.oryxos.tool.sandbox.Sandbox`
```java
package com.oryxos.tool.sandbox;

/**
 * 表达在受控环境里执行动作的抽象意图，不绑定任何特定隔离技术实现.
 */
public interface Sandbox {

  /**
   * 校验目标动作是否符合沙箱安全规则，违规时抛出异常阻断执行.
   *
   * @param action 沙箱动作描述（动作类型及目标字符串）
   * @throws SandboxViolationException 校验不通过时抛出
   */
  void enforce(SandboxAction action);

  /**
   * 非阻断式检查目标是否允许.
   *
   * @param target 目标对象
   * @return true 若允许
   */
  boolean check(String target);
}
```

### 1.2 `com.oryxos.tool.sandbox.SandboxAction`
```java
package com.oryxos.tool.sandbox;

/**
 * 沙箱动作描述值对象.
 */
public record SandboxAction(ActionType type, String target) {}
```

### 1.3 `com.oryxos.tool.sandbox.ActionType`
```java
package com.oryxos.tool.sandbox;

/**
 * 沙箱动作类型枚举.
 */
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
```

### 1.4 `com.oryxos.tool.sandbox.SandboxViolationException`
```java
package com.oryxos.tool.sandbox;

/**
 * 沙箱安全违规异常.
 */
public class SandboxViolationException extends RuntimeException {
  public SandboxViolationException(String message) {
    super(message);
  }
}
```

## 2. Configuration Properties Contracts

| 配置属性键 | 默认值 | 说明 |
|---|---|---|
| `file.allowed_paths` | `[]` | 允许读写的文件绝对或相对根目录列表 |
| `shell.allowed_commands` | `[]` | 允许执行的 Shell 可执行文件名称集合 |
| `http.allowed_domains` | `[]` | 允许出站连接的域名列表（支持 `*.domain.com` 通配符） |
