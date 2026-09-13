# Feature Specification: 一个目录定义一个会自己运行的 Agent

**Feature Branch**: `029-lesson29-agent-directory`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "第29节需求：一个目录定义一个会自己运行的 Agent。业务方通过一个自足目录声明 Agent 的运行配置、任务指令、内部子指令、参考和脚本；系统扫描、校验、注册并接入既有定时执行能力。"

## Clarifications

### Session 2026-09-13

- Q: 第 29 节课件与旧技术方案对 Skill 归属存在冲突时采用哪一版？ → A: 以修改后的第 29 节课件为准。Skill 是 Agent 目录内部资源，不建立跨 Agent 公共 Skill 库，也不使用软连接绑定。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 投放目录即可安装 Agent (Priority: P1)

作为 OryxOS 管理员，我希望把一个完整 Agent 目录放入工作区后，系统能够识别其主文件、派生运行配置并注册，这样新增业务 Agent 不需要修改底座代码。

**Why this priority**: 目录安装是 Agent OS 区别于单 Agent 框架的核心能力，也是后续定时运行和 API 管理的前提。

**Independent Test**: 在隔离工作区放入多个合法 Agent 目录并执行扫描，可以验证注册数量、每个 Agent 的配置以及定时定义均与目录声明一致。

**Acceptance Scenarios**:

1. **Given** 工作区包含一个结构完整的 Agent 目录，**When** 系统扫描该工作区，**Then** Agent 立即出现在注册列表中且其身份、Provider、工具、通知渠道和定时配置保持声明值。
2. **Given** 工作区包含 N 个合法 Agent 目录，**When** 执行一次扫描，**Then** 注册列表恰好新增 N 个 Agent，所有带定时配置的 Agent 都完成调度注册。
3. **Given** Agent 主文件缺少 name 或 provider，**When** 加载该目录，**Then** 加载失败且错误明确指出缺失字段。
4. **Given** Agent 声明了未注册的底座工具，**When** 加载该目录，**Then** 系统输出包含 Agent 名称和工具名称的明确告警。

---

### User Story 2 - 资源按需进入上下文 (Priority: P2)

作为 Agent 作者，我希望主任务指令在每次触发时进入上下文，而内部子指令、参考和脚本只在任务需要时由 Agent 使用现有能力读取或执行，从而控制上下文体积并保持指令修改即时生效。

**Why this priority**: 渐进式披露使复杂 Agent 可以携带丰富资源，同时避免每轮预载全部内容造成上下文浪费。

**Independent Test**: 构造包含主文件、子指令、参考和脚本的 Agent 目录，组装一次提示词并断言只有去除配置区的主正文被预载；随后分别通过既有文件读取和命令执行能力访问资源。

**Acceptance Scenarios**:

1. **Given** Agent 主文件同时包含配置区和正文，**When** 组装系统提示词，**Then** 正文被注入且配置区不出现在提示词中。
2. **Given** Agent 目录包含内部子指令、参考和脚本，**When** 尚未显式读取或执行这些资源，**Then** 它们的内容不出现在系统提示词中。
3. **Given** Agent 正文发生修改，**When** 下一轮重新组装上下文，**Then** 新正文立即生效而无需重启。
4. **Given** 任务需要内部资源，**When** Agent 使用已获授权的文件读取或命令执行能力，**Then** 资源可被访问且访问范围受既有权限约束。

---

### User Story 3 - 启动与运行时注册保持一致 (Priority: P3)

作为后续 Agent 管理入口的开发者，我希望启动扫描和运行时新增复用同一套注册、校验和调度逻辑，并保存调度句柄，以便下一节可以安全地新增、更新和注销 Agent。

**Why this priority**: 两条路径行为一致可以避免文件安装与 API 安装出现不同结果，调度句柄则是后续更新和删除的必要基础。

**Independent Test**: 对同一合法或非法配置分别走启动加载和运行时注册，比较注册结果、异常类型和消息；注册含定时配置的 Agent 后验证调度句柄存在且 Cron/时区一致。

**Acceptance Scenarios**:

1. **Given** 一个合法运行配置，**When** 通过运行时入口注册，**Then** 无需重启即可查询到它。
2. **Given** 一个非法运行配置，**When** 分别通过启动加载和运行时入口注册，**Then** 两条路径产生相同异常类型和相同消息格式。
3. **Given** 一个包含定时配置的 Agent，**When** 运行时注册，**Then** 每个定时任务都持有可管理句柄，且 Cron 与时区和声明一致。
4. **Given** 一个已注册 Agent，**When** 从注册中心移除，**Then** 其存在性查询立即返回否，为下一节注销流程提供基础能力。

### Edge Cases

- 工作区根目录不存在、为空或含普通文件时，扫描结果为空且系统保持可用。
- Agent 子目录缺少主文件、主文件没有完整 frontmatter、frontmatter 格式错误或正文为空时，返回可定位到目录或字段的错误。
- 主文件中的 name 与目录名不一致时拒绝加载，防止文件路径身份和运行身份分裂。
- 同一次扫描遇到一个非法 Agent 时，不得把该 Agent 部分注册；错误必须保留可诊断上下文。
- 重复注册同名 Agent 时保持单一注册项，具体冲突与版本策略留待扩展阶段。
- Agent 没有 scripts、skills 或 REFERENCE.md 时仍可正常加载。
- Agent 没有定时配置时不创建调度句柄。
- 内部资源不得因为存在于目录中就自动进入提示词。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 将工作区中每个含主文件的直接子目录识别为一个候选 Agent，并支持一次扫描多个目录。
- **FR-002**: 系统 MUST 从 Agent 主文件中分离配置区与任务正文，并识别可选的内部子指令目录、脚本目录和参考文件。
- **FR-003**: 系统 MUST 要求 Agent 名称与 Provider 配置存在；缺失时必须拒绝加载并在错误中点名字段。
- **FR-004**: 系统 MUST 要求 Agent 名称与目录名一致，确保目录身份、注册身份和运行身份唯一对应。
- **FR-005**: 系统 MUST 将 Agent 声明的身份、Provider、工具、通知渠道、定时任务、Bootstrap 和运行设置完整派生为底座可执行的运行配置。
- **FR-006**: 系统 MUST 让目录加载与运行时注册复用同一套配置校验；同一非法输入必须产生同一异常类型和消息格式。
- **FR-007**: 系统 MUST 在 Agent 引用未注册工具时记录包含 Agent 与工具名称的明确告警；不得把 Agent 目录或内部资源注册成工具。
- **FR-008**: 注册中心 MUST 支持运行时注册、移除和存在性查询，并在操作完成后立即反映最新状态。
- **FR-009**: 调度器 MUST 支持按单个 Agent 注册其全部定时任务，并保留每个任务的运行句柄；批量启动注册必须复用该单 Agent 注册流程。
- **FR-010**: 系统 MUST 在每次上下文组装时重新读取 Agent 主文件正文，去除配置区后注入系统提示词。
- **FR-011**: 系统 MUST 默认不预载 Agent 的内部子指令、参考或脚本内容；这些资源只能按任务指引通过既有文件读取或命令执行能力访问。
- **FR-012**: Agent 内部 Skill MUST 位于自身目录内，不建立跨 Agent 共享能力库、全局索引、软连接绑定或新的 Skill 执行工具。
- **FR-013**: 系统 MUST 提供 daily-reconcile 示例 Agent 目录，包含主文件、对账脚本、差异报告子指令和参考资料，且不得包含明文凭证。
- **FR-014**: 系统 MUST 明确将带脚本 Agent 视为可信代码；核心阶段必须同时校验解释器白名单与脚本位于该工作区的 Agent `scripts/` 目录内，并明确不承诺子进程网络隔离。

### Key Entities

- **Agent Directory**: 一个业务 Agent 的唯一安装单元，以目录名为身份，包含主文件及可选内部资源。
- **Agent Definition**: 从主文件解析出的配置区、正文和资源位置集合。
- **Runtime Profile**: 从 Agent Definition 派生、供既有底座执行与调度的运行配置。
- **Schedule Handle**: 一个已注册定时任务的可管理运行句柄，与 Agent 和任务标识关联。
- **Internal Resource**: Agent 私有的子指令、参考或脚本，只能按需访问。

## Scope Boundaries

本节不实现 Agent 版本管理、Agent 市场或共享、跨 Agent 能力复用、脚本容器与网络隔离、同名冲突策略、文件监听热加载、Agent 管理 REST API、通过自然语言生成 Agent，也不新增数据库表或第三方依赖。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在隔离工作区投放 N 个合法 Agent 目录后，一次扫描能注册恰好 N 个 Agent，数量与配置字段匹配率为 100%。
- **SC-002**: 对缺 name、缺 provider 和未知工具三类错误配置，100% 产生包含 Agent 或字段名称的可诊断错误或告警。
- **SC-003**: 同一非法配置经启动与运行时路径处理时，异常类型与消息完全一致。
- **SC-004**: 含定时配置的 Agent 注册后，100% 的任务生成句柄，Cron 和时区与声明完全一致。
- **SC-005**: 上下文组装结果包含主正文，同时对内部子指令、参考和脚本内容的预载命中数为 0；主正文修改在下一次组装时可见。
- **SC-006**: daily-reconcile 示例无需新增 Java 代码即可被扫描、列出并派生出可执行运行配置。
- **SC-007**: 全量既有自动化测试与本节六个 harness 测试类全部通过，无回归、无跳过或禁用。

## Assumptions

- 以用户确认的修改后第 29 节课件为本节设计基线；其中 Agent 私有 Skill 模型覆盖旧技术方案中的公共 Skill 与软连接模型。
- Agent 作者和安装者是可信管理员；脚本以 OryxOS 进程所属操作系统用户权限执行。
- Provider、工具注册中心、上下文组装、定时调度、通知、Memory 与 Sandbox 均复用第 16～28 节能力。
- Agent 管理 API、目录实时监听和完整注销/更新流程由第 30 节交付。
