# Data Model: Lesson 24 - Sandbox Domain Model

## 1. 领域模型结构

```mermaid
classDiagram
    class Sandbox {
        <<interface>>
        +void enforce(SandboxAction action)
        +boolean check(String target)
    }

    class WhitelistSandbox {
        -List~Path~ allowedRoots
        -Set~String~ allowedCommands
        -List~String~ allowedDomainPatterns
        +enforce(SandboxAction action)
        -checkFilePath(String rawPath)
        -checkShellCommand(String command)
        -checkHttpUrl(String url)
        -matchesDomain(String host, String pattern)
    }

    class SandboxAction {
        <<record>>
        +ActionType type
        +String target
    }

    class ActionType {
        <<enum>>
        FILE_READ
        FILE_WRITE
        SHELL_COMMAND
        HTTP_REQUEST
    }

    class SandboxViolationException {
        +SandboxViolationException(String message)
    }

    class FileSandboxProperties {
        <<record>>
        +List~String~ allowedPaths
    }

    class ShellSandboxProperties {
        <<record>>
        +List~String~ allowedCommands
    }

    class HttpSandboxProperties {
        <<record>>
        +List~String~ allowedDomains
    }

    Sandbox <|.. WhitelistSandbox
    Sandbox ..> SandboxAction
    SandboxAction --> ActionType
    WhitelistSandbox ..> SandboxViolationException
    WhitelistSandbox --> FileSandboxProperties
    WhitelistSandbox --> ShellSandboxProperties
    WhitelistSandbox --> HttpSandboxProperties
```

## 2. 模型定义说明

### 2.1 动作类型 (`ActionType`)
```java
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
```

### 2.2 沙箱动作描述 (`SandboxAction`)
```java
public record SandboxAction(ActionType type, String target) {
  public SandboxAction {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(target, "target must not be null");
  }
}
```

### 2.3 违规异常 (`SandboxViolationException`)
```java
public class SandboxViolationException extends RuntimeException {
  public SandboxViolationException(String message) {
    super(message);
  }
}
```

### 2.4 配置属性绑定 Record
```java
@ConfigurationProperties(prefix = "file")
public record FileSandboxProperties(List<String> allowedPaths) {
  public FileSandboxProperties {
    if (allowedPaths == null) {
      allowedPaths = List.of();
    }
  }
}

@ConfigurationProperties(prefix = "shell")
public record ShellSandboxProperties(List<String> allowedCommands) {
  public ShellSandboxProperties {
    if (allowedCommands == null) {
      allowedCommands = List.of();
    }
  }
}

@ConfigurationProperties(prefix = "http")
public record HttpSandboxProperties(List<String> allowedDomains) {
  public HttpSandboxProperties {
    if (allowedDomains == null) {
      allowedDomains = List.of();
    }
  }
}
```
