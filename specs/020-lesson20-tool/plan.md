# Implementation Plan: 第20节 Tool 体系 原理解析、实现与代码讲解

**Branch**: `020-lesson20-tool` | **Date**: 2026-09-05 | **Spec**: [spec.md](file:///e:/study/aiprogram/oryxos/specs/020-lesson20-tool/spec.md)

**Input**: Feature specification from `/specs/020-lesson20-tool/spec.md`

## Summary

构建 OryxOS 统一工具体系与 Plugin Tool 扩展底座。以 `OryxTool` 为统一抽象地基，屏蔽内置工具（文件、Shell、HTTP）、Spring AI 注解工具与外部 MCP 协议工具的来源差异，统一纳管于 `ToolRegistry` 并支持 Profile 声明粒度的精确过滤；内置涉外 IO 工具首行执行沙箱安全检查；通过 `McpClientService` 与 `McpToolAdapter` 适配外部 MCP 服务，落实故障隔离韧性（失联仅记 WARN 不阻断系统启动）。

## Technical Context

- **Language/Version**: Java 21 (LTS) with Virtual Threads enabled
- **Fixed Tech Stack**: `JDK 21 + Spring Boot 3.x + Spring AI Alibaba（动手前先跑 mvn dependency:tree 确认锁定 BOM 里目标依赖存在）、SQLite + Spring Data JPA。凭证走环境变量占位，不落明文。SQLite 用手工建表脚本，不依赖 hibernate.ddl-auto=update。`
- **Module Allocation**: `OryxTool`/`ToolResult`→oryxos-core；其余（Registry/内置 Tool/MCP）→oryxos-tool
- **Testing Strategy**: `测试策略按课件"验收 harness"执行：OryxToolContractTest、ToolRegistryTest、FileToolsTest/ShellToolsTest/HttpToolsTest、McpClientServiceTest、McpToolAdapterTest（覆盖 契约三件套非空遍历、Profile tools 精确过滤、文件/Shell/HTTP 正常与沙箱拦截、MCP 包装与执行转发、失联隔离启动不炸），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。`
- **Syntax Forbidden**: `避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## Constitution Check

- [x] **原则一：自实现 ReAct Loop**（ReAct 循环通过 `OryxTool` 抽象接口调度工具，完全自主控制执行与回填）
- [x] **原则二：Spring AI 职责限定**（仅使用 Spring AI 的 JSON Schema 契约生成与 Provider 协议转换，禁用自动工具执行，执行由 `ToolExecutor` 控制）
- [x] **原则三：Provider 显式映射**（工具模块无侵入影响 Provider 显式路由映射）
- [x] **原则四：一个目录 = 一个 Agent & 渐进式披露**（Profile 的 `tools` 字段精确过滤 Agent 可用工具集）
- [x] **原则五：审计表 Day One 写入**（所有工具执行成败均通过 `ToolExecutor` 统一记录至 `tool_invocations`）
- [x] **原则六：应用层白名单沙箱**（内置涉外 IO 工具首行必须调用 `Sandbox.enforce` 拦截越界操作）
- [x] **原则七：同步阻塞执行模型**（工具执行采用同步调用模型，由虚拟线程支撑并发，不引入响应式流）
- [x] **原则八：Tool 模块三合一**（内置 Tool、MCP Client、Sandbox Check 统一收敛在 `oryxos-tool`）

## Project Structure & Module Allocation

### Documentation (this feature)

```text
specs/020-lesson20-tool/
├── plan.md              # 本实施计划文档
├── research.md          # 架构调研与设计决策（Phase 0）
├── data-model.md        # 工具元数据与配置实体模型（Phase 1）
├── quickstart.md        # 验收验证与执行指南（Phase 1）
├── contracts/           # 契约定义
│   └── tool-contracts.md
└── tasks.md             # 任务依赖拆解清单（Phase 2）
```

### Source Code Allocation

- **oryxos-core**:
  - `com.oryxos.core.OryxTool`（统一抽象接口：`getName()`, `getDescription()`, `getInputSchema()`, `execute(String inputJson)` 及便捷重载）
  - `com.oryxos.core.model.ToolResult`（增加 record 风格别名方法：`success()`, `content()`, `errorMessage()`, `retryable()`，完全兼容现有 getter）
- **oryxos-tool**:
  - `com.oryxos.tool.ToolRegistry`（增强 `filterTools(List<String> allowedToolNames)` 支持 Profile 粒度精确匹配，`contains(String name)`）
  - `com.oryxos.tool.builtin.FileTools`（内置 3 个工具：`read_file`, `write_file`, `list_dir`，首行 `Sandbox.enforce`）
  - `com.oryxos.tool.builtin.ShellTools`（内置 1 个工具：`shell`，argv 直传、超时控制、首行 `Sandbox.enforce`）
  - `com.oryxos.tool.builtin.HttpTools`（内置 2 个工具：`http_get`, `http_post`，域名白名单、SSRF 防护、首行 `Sandbox.enforce`）
  - `com.oryxos.tool.builtin.NotifyTools`（19 节已交付，确保契约三件套满足参数化测试）
  - `com.oryxos.tool.mcp.McpServerConfig`（MCP 配置模型：name, transport, command, env, url）
  - `com.oryxos.tool.mcp.McpToolSpec`（MCP 工具元数据规格：name, description, inputSchema）
  - `com.oryxos.tool.mcp.McpClient`（MCP Client 抽象接口，包含 `listTools()`, `callTool(String name, JsonNode arguments)`）
  - `com.oryxos.tool.mcp.McpToolAdapter`（实现 `OryxTool`，将 MCP 远端工具适配为统一抽象）
  - `com.oryxos.tool.mcp.McpClientService`（加载 `.oryxos/mcp_servers.yaml`，初始化连接并包装注册工具，异常 WARN 隔离跳过）

### Tests Allocation

- **oryxos-tool**:
  - `com.oryxos.tool.contract.OryxToolContractTest`（参数化测试遍历 ToolRegistry 所有工具：name/description/inputSchema 非空）
  - `com.oryxos.tool.ToolRegistryTest`（多来源注册、按 Profile tools 字段精确过滤不多不少）
  - `com.oryxos.tool.builtin.FileToolsTest`（read_file/write_file/list_dir 正常执行 + 越界被拦截）
  - `com.oryxos.tool.builtin.ShellToolsTest`（shell 命令正常执行 + 超时或非白名单被拦截）
  - `com.oryxos.tool.builtin.HttpToolsTest`（http_get/http_post 正常执行 + 越界域名被拦截）
  - `com.oryxos.tool.mcp.McpToolAdapterTest`（调用参数原样转发，返回包装为 ToolResult，失败标记可重试）
  - `com.oryxos.tool.mcp.McpClientServiceTest`（单 server 失联记 WARN 且启动不崩，健康 server 工具正常注册）

## Test Strategy

- **课件验收 Harness 关键回归点**：
  1. `OryxToolContractTest`：参数化测试遍历 `ToolRegistry` 中所有工具，断言 `getName()`、`getDescription()`、`getInputSchema()` 均非空。
  2. `ToolRegistryTest`：内置工具与 MCP 工具统一以 `OryxTool` 注册进注册表；按 Profile 声明的 `tools` 列表过滤后，返回子集精确匹配声明，不多不少。
  3. `FileToolsTest` / `ShellToolsTest` / `HttpToolsTest`：正常场景功能正确；命中沙箱白名单外或非法越界时被安全拦截并抛出异常。
  4. `McpToolAdapterTest` / `McpClientServiceTest`：外部 MCP 依赖失联隔离（只 WARN 不崩，其余工具正常注册）；转发参数与返回结果封装正确。
- **构建门禁**：`mvn clean verify` 全绿（含 Spotless 格式化、P3C 阿里规范、Checkstyle、SpotBugs、OWASP）。
