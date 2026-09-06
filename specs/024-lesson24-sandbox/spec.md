# Feature Specification: Lesson 24 - Sandbox Implementation and Hardening

**Feature Branch**: `024-lesson24-sandbox`  
**Created**: 2026-09-06  
**Status**: Draft  
**Input**: 第24节需求：Sandbox 沙箱安全——砌一道隔离应用层与下层执行环境的墙，实现白名单校验并接进内置 Tool

---

## 1. Background & Value (背景与价值)

在 Agent 运行过程中，大模型生成的参数（如文件路径、Shell 命令、HTTP URL）具有天然的不确定性和潜在危害。为防止 Agent 误操作越界或执行高危动作，需要在物理执行真实 IO 之前砌一道"墙"，将危险动作在应用层拦截。

隔离机制未来将从应用层白名单升级为容器隔离（Docker/containerd）、microVM（Kata/Firecracker）乃至物理机隔离。为了不让核心阶段的实现选择绑死未来的架构演进，必须遵循"接口先行、实现解耦"原则，先定死中立的沙箱抽象契约（表达"在受控环境里执行一个动作"的意图），核心阶段落地第一档白名单实现（`WhitelistSandbox`），使未来升级多档隔离实现时调用方 Tool 一行代码不改。

---

## 2. User Scenarios & Testing (用户场景与独立测试)

### User Story 1 - 跨平台文件路径白名单校验与防穿越防护 (Priority: P1)
作为系统管理员，我希望 Agent 只能在明确配置的工作区路径白名单内读写文件。当 Agent 尝试访问白名单外路径，或使用 `..` 构造相对路径试图爬出工作区时，沙箱能立即阻断并报错，确保宿主机敏感文件（如 `/etc/passwd`、`../secret.txt`）不被越界窃取或篡改。

**Why this priority**: 文件系统读写是最基础、最直接带来数据泄露与破坏的风险敞口，必须优先封堵。

**Independent Test**:
- 在白名单内配置指定目录，传入该目录内文件路径，校验无异常放行；
- 传入白名单外路径或带 `..` 穿越序列的恶意路径，断言抛出 `SandboxViolationException`，且底层文件物理 IO 绝不发生。

**Acceptance Scenarios**:
1. **Given** 文件白名单为 `/workspace`，**When** Agent 请求读取 `/workspace/data.txt`，**Then** 沙箱校验通过。
2. **Given** 文件白名单为 `/workspace`，**When** Agent 请求读取 `/workspace/../../outside/secret.txt`，**Then** 沙箱抛出 `SandboxViolationException`，拦截访问。

---

### User Story 2 - Shell 命令可执行文件首 Token 白名单校验 (Priority: P1)
作为运维工程师，我希望限制 Agent 只能执行预先审定的安全命令（如 `ls`, `git`, `ps`）。当 Agent 生成不在白名单内的命令（如 `rm`, `curl`, `chmod`）或通过前导空格、参数组合混淆时，沙箱能提取首个可执行文件 token 进行严格拦截，杜绝恶意进程启动。

**Why this priority**: Shell 命令直连操作系统进程，若放行任意命令，将导致宿主机直接沦陷。

**Independent Test**:
- 配置白名单包含 `ls`, `git`；
- 传入 `ls -la`、`  ls /tmp `，校验放行；
- 传入 `rm -rf /` 或 `curl http://evil.com`，断言抛出 `SandboxViolationException`，且底层 `ProcessBuilder` 绝不执行。

**Acceptance Scenarios**:
1. **Given** 命令白名单包含 `ls`，**When** Agent 执行 `  ls -la /workspace `，**Then** 提取 token `ls` 比对成功，放行。
2. **Given** 命令白名单仅包含 `ls`，**When** Agent 执行 `rm -f file.txt`，**Then** 提取 token `rm` 比对失败，抛出 `SandboxViolationException` 拦截。

---

### User Story 3 - HTTP 域名白名单与通配符严格防护 (Priority: P1)
作为安全审计员，我希望 Agent 发起的出站网络请求（HTTP GET/POST 以及 Webhook 通知）受到域名白名单管控。沙箱支持精确域名和通配符域名（`*.example.com`），并严格处理点号边界，防止形似域名（如 `evil-example.com`）的伪装绕过，防止数据外流或 SSRF 攻击。

**Why this priority**: 网络请求是数据窃取与内网穿透（SSRF）的核心手段，出站域名管控是零信任网络的第一道防线。

**Independent Test**:
- 配置白名单包含 `api.example.com` 与 `*.corp.internal`；
- 传入 `https://api.example.com/v1/query` 与 `https://sub.corp.internal/notify`，校验放行；
- 传入 `https://evil-corp.internal`（形似域名但无点号子域边界），断言抛出 `SandboxViolationException`，底层 HTTP 请求绝不发出。

**Acceptance Scenarios**:
1. **Given** 域名白名单为 `*.example.com`，**When** Agent 访问 `https://api.example.com/data`，**Then** 成功匹配通配符，放行。
2. **Given** 域名白名单为 `*.example.com`，**When** Agent 访问 `https://evil-example.com/data`，**Then** 严格识别点号边界不符，抛出异常拦截。

---

### User Story 4 - 既有 Tool 体系无缝接驳与审计回填 (Priority: P2)
作为 ReAct 循环引擎，当 `FileTools`、`ShellTools`、`HttpTools`、`NotifyTools` 执行被沙箱阻断时，违规信息应被 `ToolExecutor` 统一捕获，并作为普通工具执行失败落库 `tool_invocations`（`success=false`，`error_message` 包含具体原因），随后将友好错误信息回灌给大模型，驱动模型纠偏自省。

**Why this priority**: 安全拦截必须与业务闭环结合，既要保全审计痕迹，又要让 Agent 理解违规原因以便重试或向用户报告。

**Independent Test**:
- 触发 `FileTools.readFile` 访问非法路径；
- 断言 `ToolExecutor` 返回 `ToolResult.failure`，数据库 `tool_invocations` 成功记录单次执行记录，且 `success` 为 `false`。

---

## 3. Edge Cases (边界条件与防御)

1. **白名单配置为空**：若 `file.allowed_paths`、`shell.allowed_commands` 或 `http.allowed_domains` 未配置或为空列表，必须按"全闭原则"拒绝该类型的所有操作，严禁默认全部放行。
2. **路径穿越注入**：路径中包含 `..`、`./`、连续斜杠 `//` 时，使用 `Path.normalize().toAbsolutePath()` 规范化后再判定 `startsWith`，彻底消除相对路径逃逸。
3. **Shell 首 Token 处理**：输入命令包含前导空格、Tab、换行或空字符串时，安全裁剪后提取首个 token，若为空则直接拒绝。
4. **URL 格式畸形**：传入无效或畸形 URL 导致 `URI.create` 失败时，抛出包含明确提示的 `SandboxViolationException`，不泄漏内部未受控堆栈。
5. **通配符域名点号边界**：对于 `*.example.com`，仅放行子域名（如 `foo.example.com`、`a.b.example.com`）或精确域名 `example.com`，坚决拦截 `badexample.com` 或 `fake-example.com`。

---

## 4. Requirements (功能需求清单)

### Functional Requirements
- **FR-001**: 系统 MUST 定义中立的 `Sandbox` 接口，仅暴露 `void enforce(SandboxAction action)` 方法，不包含任何白名单或具体实现特有概念。
- **FR-002**: 系统 MUST 定义 `SandboxAction` 动作描述值对象，仅包含动作类型与目标操作字符串（如路径、命令、URL）。
- **FR-003**: 系统 MUST 统一 `ActionType` 枚举，支持文件读取（`FILE_READ`）、文件写入（`FILE_WRITE`）、Shell 命令执行（`SHELL_COMMAND`）以及 HTTP 网络请求（`HTTP_REQUEST`）。
- **FR-004**: 系统 MUST 定义专用运行时异常 `SandboxViolationException`，当动作违规时抛出，携带人类可读的拦截原因。
- **FR-005**: 系统 MUST 提供 `WhitelistSandbox` 实现类，支持从 Spring 配置属性绑定文件根路径、Shell 命令与 HTTP 域名白名单。
- **FR-006**: 系统 MUST 支持通过 `FileSandboxProperties`（`file.allowed_paths`）、`ShellSandboxProperties`（`shell.allowed_commands`）、`HttpSandboxProperties`（`http.allowed_domains`）注入白名单规则。
- **FR-007**: 系统 MUST 在白名单配置为空时遵循全闭原则，拒绝一切未经白名单授权的操作。
- **FR-008**: 系统 MUST 在 `FileTools`、`ShellTools`、`HttpTools`、`NotifyTools` 执行具体 IO 前的首行强制调用 `sandbox.enforce(action)`。
- **FR-009**: 系统 MUST 确保沙箱拦截发生时，底层文件读写、进程创建、网络连接等物理 IO 绝对不被执行。
- **FR-010**: 系统 MUST 复用 `ToolExecutor` 现有的异常处理与审计链路，将沙箱拦截异常原样记入 `tool_invocations` 审计表（`success=false`）。

---

## 5. Non-Goals & Boundaries (明确不做)

1. **不做容器隔离**：核心阶段不实现 Docker / Containerd / OCI 容器运行环境。
2. **不做微虚拟机隔离**：核心阶段不实现 Kata / Firecracker / gVisor 虚拟机启动与交互。
3. **不改动 ToolExecutor 审计机制**：沙箱拦截走普通工具执行失败审计分支，不新增专用表或审计接口。
4. **不做动态沙箱管理 API**：动态增删改查白名单的端点属于第 26 节 Web Service 范围。

---

## 6. Acceptance Criteria (验收标准与 Harness)

### 自动化 Harness 测试套件（`WhitelistSandboxTest` & 工具回归）
1. `相对路径穿越必须被拦`：使用 `/workspace/../../outside/secret.txt` 触发，断言抛出 `SandboxViolationException`。
2. `通配符域名_不能被形似域名绕过`：配置 `*.example.com`，断言 `https://api.example.com/x` 放行，`https://evil-example.com/x` 必拦截。
3. `文件白名单内放行与白名单外拦截`：白名单内文件正常访问，白名单外拒绝。
4. `Shell命令白名单与首Token提取`：前导空格安全解析，白名单内命令放行，未授权命令（如 `rm`）拦截。
5. `配置为空全闭原则`：空配置情况下，任意文件、Shell、HTTP 操作全部被拒绝。
6. `内置工具接入拦截`：`FileTools`、`ShellTools`、`HttpTools`、`NotifyTools` 在沙箱拒绝时抛出异常且未调用实际 IO 执行器。

### 人工验收项
- 真实链路验证：配置仅允许 `ls`，真跑一次 `rm`，验证控制台及 `tool_invocations` 结构化落库。
- 接口中立性自查：以 microVM 架构代入验证 `Sandbox.enforce(SandboxAction)` 契约无需增加方法。
- 配置边界核实：确认 `application.yaml` 缺省白名单规则安全可靠。
