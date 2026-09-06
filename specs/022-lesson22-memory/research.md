# Technical Research: 第22节 Memory 模块实现

## 1. 核心架构决策

### 决策一：单文件两分区 Markdown 存储 (`MEMORY.md`)
- **背景**：长期记忆分为"核心记忆"（用户根本偏好、常驻事实，始终在场）与"归档记忆"（历史流水、事件记录，可截断）。
- **决策**：采用单一 Markdown 文件 `.oryxos/memory/MEMORY.md`，通过二级标题 `## 核心记忆` 和 `## 归档记忆` 进行分区物理存储。
- **依据**：零额外依赖、人可读、git 可跟踪。避免为核心记忆单独建表或增加新类，杜绝过度设计。

### 决策二：零内存缓存（Zero Caching）
- **背景**：ReAct 循环中，Agent 在某一轮调用 `save_memory` 保存信息后，下一轮推理即需要该记忆生效。
- **决策**：`LongTermMemory` 绝不使用 Java 堆内缓存（不使用 ConcurrentHashMap、Guava Cache 或 Spring Cache），每次 `load()`、`append()`、`recallByKeyword()` 均直接读写底层物理文件。
- **依据**：避免缓存失效同步难题，保障"写后立读"的确定性体验。小文件（数千字符）读写对现代 NVMe 耗时微秒级，完全满足单体阻塞模型性能要求。

### 决策三：严格隔离的归档截断策略（截断保核心）
- **背景**：长期记忆超出阈值（`MAX_ARCHIVE_CHARS = 4000`）时需进行容量截断以防止撑爆 LLM 上下文。
- **决策**：截断函数 `truncateIfNeeded` 物理隔离，入参仅接收归档区文本切片。截断时仅丢弃归档区头部的历史条目，保留最新的 4000 字符。核心记忆区由 `extractSection` 完整提取，物理上不经过截断函数，实现"核心记忆一字不能少"。
- **依据**：守住核心记忆"始终在场"的根本底线。

### 决策四：Agent 显式指定写入分区（系统不猜测）
- **背景**：新记忆究竟是核心事实还是归档流水。
- **决策**：`save_memory` 暴露可选参数 `scope`（`core` 或 `archival`），缺省值为 `archival`。由 Agent 自主在入参中决定，系统不做模糊推测。
- **依据**：大模型具备语义分类能力，系统猜测往往引入意外覆盖。

### 决策五：不区分大小写的关键词包含检索
- **背景**：核心阶段长期记忆检索的定位。
- **决策**：仅针对归档记忆区进行逐行检索，使用小写化包含匹配 `contains`。未命中时返回字符串 `"没有找到相关记忆"`，不抛异常。核心记忆区天然全量注入 Prompt，不参与检索。
- **依据**：核心阶段最短链路，避免引入复杂分词与向量检索依赖，保持极简。

### 决策六：跨模块接口解耦与依赖倒置
- **背景**：`PromptBuilder` 位于 `oryxos-core`，需要调用 `MemoryService.buildContext(session)`，但 `oryxos-core` 不能依赖 `oryxos-memory`。
- **决策**：`MemoryService` 接口与 `MemoryScope` 枚举位于 `oryxos-core`（包路径 `com.oryxos.memory`），与 `ProviderService`、`SessionManager` 架构风格一致；`MemoryServiceImpl`、`LongTermMemory`、`MemoryTools` 位于 `oryxos-memory`。
- **依据**：严格遵守架构宪法"跨模块契约统一定义在 oryxos-core，由下游模块实现（依赖倒置），严禁模块间循环依赖"。
