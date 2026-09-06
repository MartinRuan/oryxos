# Research: Lesson 24 - Sandbox Architecture and Hardening

## 1. 核心技术调研与决策 (Key Technical Decisions)

### 1.1 接口先行与意图抽象
- **意图表达**：`Sandbox` 接口仅保留 `void enforce(SandboxAction action)` 方法。该方法表达"在受控环境里执行一个动作"这一核心意图，完全不暴露"白名单""容器镜像"或"虚拟化配置"等具体实现概念。
- **扩展性验证**：当未来演进至容器（Docker/containerd）或微虚拟机（Kata/Firecracker/gVisor）时，`SandboxAction` 的 `type` 与 `target` 仍可直接映射为容器挂载卷/系统调用过滤（seccomp）或轻量 VM 内部执行请求，调用方（`FileTools`、`ShellTools`、`HttpTools` 等）无需任何代码改动。

### 1.2 路径规范化与防穿越方案
- **威胁分析**：恶意模型可能通过 `../` 向上攀爬跳出白名单根目录，或通过软链接绕过判定。
- **解决方案**：
  - 白名单根目录在初始化时通过 `Path.of(p).toAbsolutePath().normalize()` 固化。
  - 待校验路径通过 `Path.of(rawPath).toAbsolutePath().normalize()` 消除所有 `.` 和 `..`。
  - 使用 `target.startsWith(root)` 进行判定。只有规范化后的绝对路径是白名单根目录的子路径时才放行。

### 1.3 Shell 命令首 Token 提取
- **命令解析规则**：Shell 命令可能包含前导空白字符、Tab、换行或多个连续空格。
- **解决方案**：
  - 先执行 `trim()`，去除前后空白。
  - 使用空白正则 `\\s+` 切割，提取第一个非空 token 作为可执行文件名称。
  - 比对 `Set<String> allowedCommands`，精确包含才放行。

### 1.4 HTTP 域名匹配与点号边界防护
- **漏洞模式**：如果使用 `host.endsWith("example.com")`，则 `evil-example.com` 会被误判为命中 `example.com` 白名单。
- **解决方案**：
  - 精确域名匹配：`pattern.equalsIgnoreCase(host)`。
  - 通配符匹配（如 `*.example.com`）：去掉前导 `*` 后得到 `.example.com`。目标 host 必须以 `.example.com` 结尾（即子域名），或者目标 host 等于 `example.com`（若通配符规则允许根域）。严格保证点号边界。

### 1.5 严格遵循"配置为空即全闭"
- **安全原则**：若未配置白名单（如列表为空或属性缺失），必须默认拒绝所有请求，坚决杜绝"未配置即放行"的不安全默认行为。

### 1.6 语法禁区与门禁约束
- 避免使用 Java 18+ 导致 P3C/PMD/ASM 解析异常的语法形态（如增强 switch 语句中的箭头标签或带有表达式模式匹配）。
- 维持标准 Java 21 LTS 规范，Checkstyle 每行 <= 100 字符。
