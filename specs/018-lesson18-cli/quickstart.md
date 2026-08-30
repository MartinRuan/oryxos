# Quickstart & Verification Guide: 第18节 CLI 与会话持久化

## 1. 自动化 Harness 测试验证

运行核心单测与存储层仓储测试：

```bash
mvn test -pl oryxos-core -Dtest=SessionManagerTest
mvn test -pl oryxos-storage -Dtest=SessionRepositoryTest
```

运行全量规约门禁校验：
```bash
mvn clean verify
```

## 2. CLI 交互式本地验证

### 验证轻命令（不启动 Spring，秒回）：
```bash
# 查看版本与帮助
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar --help

# 列出 profile
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar profile list
```

### 验证交互式对话（重命令，启动 Spring）：
```bash
# 启动 chat
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar chat --profile default
```
交互流程：
1. 终端提示 `> `
2. 输入提问：`你好`
3. 模型返回答复
4. 输入 `/quit` 退出对话
