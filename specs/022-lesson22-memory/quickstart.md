# Quickstart: 第22节 Memory 模块快速验证指南

## 1. 自动化测试套件执行

在项目根目录下执行针对 `oryxos-memory` 模块及关联回归的测试：

```bash
mvn test -pl oryxos-memory,oryxos-core -am
```

## 2. 关键回归点验证

确保以下核心回归测试通过：
1. `com.oryxos.memory.LongTermMemoryTest#截断只裁归档区_核心记忆一字不能少`
2. `com.oryxos.memory.LongTermMemoryTest#写入后立刻可读_不允许有缓存`
3. `com.oryxos.memory.LongTermMemoryTest#scope路由到正确区块_缺省写入归档区`
4. `com.oryxos.memory.LongTermMemoryTest#recallByKeyword只搜归档区_不区分大小写`
5. `com.oryxos.memory.MemoryToolsTest#scope缺省写归档`
6. `com.oryxos.memory.MemoryToolsTest#关键词未命中返回友好提示不报错`
7. `com.oryxos.memory.MemoryServiceTest#buildContext返回核心记忆与会话历史的组合_归档区不整体注入`

## 3. 静态检查与质量门禁

运行完整构建验证门禁（含 Spotless、Checkstyle、P3C-PMD、SpotBugs、OWASP）：

```bash
mvn clean verify
```
