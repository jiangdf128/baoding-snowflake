# 宝顶云原生雪花算法 - 中文文档

## 项目简介

宝顶雪花算法（BaoDing Snowflake）是一个专为云原生环境设计的分布式ID生成器，采用经典的雪花ID思想，并在其基础上进行了多项工业级优化。

### 核心优势

| 特性 | 说明 |
|------|------|
| **8ms时间窗口** | 相比传统1ms窗口，单实例QPS提升至51.2万（业界平均12万） |
| **仅依赖Redis** | 无需ZooKeeper等复杂组件，运维成本极低 |
| **K8s原生支持** | Pod启停自动申请/释放WorkerId，完美支持弹性伸缩 |
| **渐进式时间回拨处理** | 宁可"追回"也不拒绝服务，保证服务连续性 |
| **JVM退出钩子** | 确保应用退出时正确保存状态并释放资源 |

---

## 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>top.baoding</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle依赖

```kotlin
implementation("top.baoding:baoding-snowflake:1.0.0")
```

---

## 配置示例

### Local模式（开发环境）

```yaml
baoding:
  snowflake:
    mode: local
    datacenter-id: 0
    workstation-id: 0
  data-dir:
    path: /tmp/snowflake-data
```

### Redis模式（生产环境）

**要求：Redis 8.0+**

```yaml
baoding:
  snowflake:
    mode: redis
    datacenter-id: 0
    scope: ${spring.application.name}
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## API使用

### 基本用法

```java
import top.baoding.snowflake.core.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdController {

    @Autowired
    private IdGenerator idGenerator;

    @GetMapping("/next-id")
    public long nextId() {
        return idGenerator.nextId();
    }
}
```

### ID结构解析

```java
import top.baoding.snowflake.util.HexIdUtils;

long id = idGenerator.nextId();
System.out.println("Hex: " + HexIdUtils.toHex(id));
System.out.println("Binary: " + HexIdUtils.toBinary(id));
```

---

## 架构设计

详见 [ARCHITECTURE.md](ARCHITECTURE.md)

---

## 部署指南

详见 [DEPLOYMENT.md](DEPLOYMENT.md)

---

## 与其他方案对比

详见 [COMPARISON.md](COMPARISON.md)