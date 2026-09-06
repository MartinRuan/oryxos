# Implementation Plan: Lesson 24 - Sandbox Implementation and Hardening

**Branch**: `024-lesson24-sandbox` | **Date**: 2026-09-06 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/024-lesson24-sandbox/spec.md)

**Input**: Feature specification from `specs/024-lesson24-sandbox/spec.md`

## Summary

实现应用层白名单安全沙箱（`WhitelistSandbox`），并在物理执行真实 IO 前将其作为第一道防线接入已有的内置工具（`FileTools`、`ShellTools`、`HttpTools`、`NotifyTools`）。遵循"接口先行、实现解耦"原则，先定死中立的 `Sandbox` 抽象接口（仅暴露 `enforce(SandboxAction)`），保证未来无缝扩展容器或 microVM 等更高隔离等级实现。

## Technical Context

- **固定技术栈**: JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。
- **模块落位**: `sandbox` 包及相关实现全部落位在 `oryxos-tool` 模块。
- **测试策略**: 测试策略按课件"验收 harness"执行：`WhitelistSandboxTest`（覆盖路径穿越拦截、通配符域名边界、命令首 token 提取、空配置全闭拦截），及 `FileTools`/`ShellTools`/`HttpTools`/`NotifyTools` 拦截回归测试（验证抛出 `SandboxViolationException` 且底层物理 IO 绝不发生），单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成的定义是 `mvn clean verify` 全绿。
- **语法禁区**: 避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。

## Constitution Check

1. **自实现 ReAct Loop**：沙箱作为工具执行的前置门禁，在 Tool 内拦截，不干扰也不引入 Spring AI Agent 抽象。
2. **Spring AI 职责限定**：仅做 Schema 生成与协议转换，不接管 Tool 执行，沙箱校验完全在 OryxOS `ToolExecutor` / `OryxTool` 内部完成。
3. **禁用 Java SecurityManager**：明确使用应用层白名单沙箱（`WhitelistSandbox`），不依赖废弃的 JVM 安全管理器。
4. **Day One 审计表写入**：`SandboxViolationException` 被 `ToolExecutor` 捕获后，自动作为普通工具失败记录至 `tool_invocations`（`success=false`，`error_message` 记录具体拦截原因）。
5. **代码规范与门禁**：Checkstyle（每行 <= 100 字符）、Spotless（Google Java Format）、Alibaba P3C（无魔法值、标准 Javadoc）、SpotBugs 零告警。

## Project Structure

### Documentation
```text
specs/024-lesson24-sandbox/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── sandbox-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code
```text
oryxos-tool/src/main/java/com/oryxos/tool/
├── sandbox/
│   ├── Sandbox.java                     # 中立接口（保持不变，已定死）
│   ├── SandboxAction.java               # 动作值对象（type, target）
│   ├── ActionType.java                  # 动作类型枚举（FILE_READ, FILE_WRITE, SHELL_COMMAND, HTTP_REQUEST）
│   ├── SandboxViolationException.java   # 沙箱违规异常
│   ├── FileSandboxProperties.java       # file.allowed_paths 绑定 Record
│   ├── ShellSandboxProperties.java      # shell.allowed_commands 绑定 Record
│   ├── HttpSandboxProperties.java       # http.allowed_domains 绑定 Record
│   └── WhitelistSandbox.java            # 核心实现：路径防穿越、Shell首token比对、通配符域名边界
├── config/
│   └── ToolAutoConfiguration.java       # 自动装配 WhitelistSandbox 替换 DefaultSandbox
└── builtin/
    ├── FileTools.java                   # execute 首行 enforce(FILE_READ/FILE_WRITE, path)
    ├── ShellTools.java                  # execute 首行 enforce(SHELL_COMMAND, command)
    ├── HttpTools.java                   # execute 首行 enforce(HTTP_REQUEST, url)
    └── NotifyTools.java                 # execute 首行 enforce(HTTP_REQUEST, url)

oryxos-tool/src/test/java/com/oryxos/tool/
├── sandbox/
│   └── WhitelistSandboxTest.java        # 覆盖路径穿越、通配符域名边界、命令首token、空配置全闭等
└── builtin/
    ├── FileToolsTest.java               # 拦截与物理 IO never() 回归
    ├── ShellToolsTest.java              # 拦截与物理 IO never() 回归
    ├── HttpToolsTest.java               # 拦截与物理 IO never() 回归
    └── NotifyToolsTest.java             # 拦截与物理 IO never() 回归
```
