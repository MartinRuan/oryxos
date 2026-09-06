# Data Model: 第22节 Memory 模块数据模型

## 1. 记忆作用域枚举 (MemoryScope)

```java
package com.oryxos.memory;

/**
 * 长期记忆存储作用域.
 */
public enum MemoryScope {
  /**
   * 核心记忆区：用户常驻偏好与根本事实，永远全量加载，不参与截断与关键词检索.
   */
  CORE,

  /**
   * 归档记忆区：历史事件流水与次要事实，可按 4000 字符限制截断，支持关键词检索.
   */
  ARCHIVAL
}
```

## 2. 长期记忆文件结构 (`MEMORY.md`)

- **文件物理路径**：`.oryxos/memory/MEMORY.md`
- **默认模板**：
```markdown
## 核心记忆

## 归档记忆
```
- **条目写入规范**：
  - 前缀：换行 + 减号 + 空格
  - 日期标签：`[YYYY-MM-DD]`（取系统当前本地日期 `LocalDate.now()`）
  - 示例：
```markdown
## 核心记忆
- [2026-09-05] 用户叫小王，偏好用 Java

## 归档记忆
- [2026-09-05] 归档流水 0
- [2026-09-05] 归档流水 1
```

## 3. 工具交互输入模型 (Tool Input Schema)

### 3.1 `save_memory`
```json
{
  "type": "object",
  "properties": {
    "content": {
      "type": "string",
      "description": "要记住的内容"
    },
    "scope": {
      "type": "string",
      "description": "core 或 archival，不确定就填 archival"
    }
  },
  "required": ["content"]
}
```

### 3.2 `recall_memory`
```json
{
  "type": "object",
  "properties": {
    "keyword": {
      "type": "string",
      "description": "检索关键词"
    }
  },
  "required": ["keyword"]
}
```
