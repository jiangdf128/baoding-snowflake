# BaoDing Snowflake / 宝顶云原生雪花算法

[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue.svg)](https://unlicense.org)
[![Java](https://img.shields.io/badge/Java-25-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)

[中文说明](#中文) | [English](#english)

---

<a name="中文"></a>

## 中文

### 什么是宝顶雪花算法？

宝顶雪花算法（BaoDing Snowflake）是一个专为云原生环境设计的分布式ID生成器，采用经典的雪花ID思想，并在其基础上进行了多项工业级优化。

### 核心参数对比

| 方案 | 时间窗口 | 单实例QPS | 最大实例数 | WorkerId管理 | 依赖 |
|-----|---------|----------|-----------|-------------|------|
| **宝顶雪花** | **8ms** | **51.2万** | **8192** | **Redis自动** | **仅Redis** |
| Twitter Snowflake | 1ms | 12万 | 1024 | 手动/ZK | Zookeeper |
| 百度UidGenerator | 1ms | 600万 | - | 数据库 | MySQL |
| 腾讯Leaf | 1ms | 50万~1000万 | - | ZK/数据库 | Zookeeper+MySQL |

> **最大实例数**：8192 = 1024(工作站) × 8(数据中心)，支持 K8s 横向扩展到极致规模。

### 核心优势

- **8ms时间窗口**：相比传统1ms窗口，单实例QPS提升至51.2万（业界平均12万）
- **仅依赖Redis**：无需ZooKeeper等复杂组件，运维成本极低
- **K8s原生支持**：Pod启停自动申请/释放WorkerId，完美支持弹性伸缩
- **渐进式时间回拨处理**：宁可"追回"也不拒绝服务，保证服务连续性
- **JVM退出钩子**：确保应用退出时正确保存状态并释放资源

### 快速开始

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.jiangdf128</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.1</version>
</dependency>
```

```kotlin
// Gradle
implementation("io.github.jiangdf128:baoding-snowflake:1.0.1")
```

### 配置示例

**Local模式（开发环境）**：

```yaml
baoding:
  snowflake:
    mode: local
    datacenter-id: 0
    workstation-id: 0
```

**Redis模式（生产环境，需要Redis 8.0+）**：

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

### 文档

- [中文快速入门](docs/README-zh.md)
- [架构设计文档](docs/ARCHITECTURE.md)
- [部署指南](docs/DEPLOYMENT.md)

---

<a name="english"></a>

## English

### What is BaoDing Snowflake?

BaoDing Snowflake is a distributed ID generator designed specifically for cloud-native environments, based on the classic Snowflake ID algorithm with industrial-grade optimizations.

### Core Parameters Comparison

| Implementation | Time Window | Single QPS | Max Instances | WorkerId Mgmt | Dependencies |
|---------------|-------------|-----------|--------------|--------------|--------------|
| **BaoDing** | **8ms** | **512K** | **8192** | **Redis Auto** | **Redis Only** |
| Twitter Snowflake | 1ms | 120K | 1024 | Manual/ZK | Zookeeper |
| Baidu UidGenerator | 1ms | 6M | - | Database | MySQL |
| Tencent Leaf | 1ms | 500K~10M | - | ZK/DB | Zookeeper+MySQL |

> **Max Instances**: 8192 = 1024 (workers) × 8 (datacenters), supporting extreme K8s horizontal scaling.

### Core Advantages

- **8ms Time Window**: Single instance QPS up to 512,000 (vs industry average 1.0.100)
- **Redis-Only Dependency**: No ZooKeeper or other complex components required
- **K8s Native Support**: Auto allocate/release WorkerId on Pod start/stop, perfect for elastic scaling
- **Graceful Clock Backward Handling**: Catching up rather than rejecting service, ensuring continuity
- **JVM Shutdown Hook**: Ensures proper state saving and resource release on application exit

### Quick Start

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.jiangdf128</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.1</version>
</dependency>
```

```kotlin
// Gradle
implementation("io.github.jiangdf128:baoding-snowflake:1.0.1")
```

### Configuration Examples

**Local Mode (Development)**:

```yaml
baoding:
  snowflake:
    mode: local
    datacenter-id: 0
    workstation-id: 0
```

**Redis Mode (Production, requires Redis 8.0+)**:

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

### Documentation

- [English Quick Start](docs/README-en.md)
- [Architecture Documentation](docs/ARCHITECTURE.md)
- [Deployment Guide](docs/DEPLOYMENT.md)

---

## Contributing / 贡献

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

详细贡献指南请查看 [CONTRIBUTING.md](CONTRIBUTING.md)。

## License / 许可证

This project is dedicated to the public domain under [The Unlicense](LICENSE).

本项目采用 [The Unlicense](LICENSE) 协议，将软件贡献于公共领域。