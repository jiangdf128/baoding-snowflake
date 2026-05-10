# BaoDing Snowflake - English Documentation

## Overview

BaoDing Snowflake is a distributed ID generator designed specifically for cloud-native environments, based on the classic Snowflake ID algorithm with industrial-grade optimizations.

### Core Advantages

| Feature | Description |
|---------|-------------|
| **8ms Time Window** | Single instance QPS up to 512,000 (vs industry average 120,000) |
| **Redis-Only Dependency** | No ZooKeeper or other complex components required |
| **K8s Native Support** | Auto allocate/release WorkerId on Pod start/stop, perfect for elastic scaling |
| **Graceful Clock Backward Handling** | Catching up rather than rejecting service, ensuring continuity |
| **JVM Shutdown Hook** | Ensures proper state saving and resource release on application exit |

---

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>io.github.jiangdf128</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle Dependency

```kotlin
implementation("io.github.jiangdf128:baoding-snowflake:1.0.0")
```

---

## Configuration Examples

### Local Mode (Development)

```yaml
baoding:
  snowflake:
    mode: local
    datacenter-id: 0
    workstation-id: 0
  data-dir:
    path: /tmp/snowflake-data
```

### Redis Mode (Production)

**Requirement: Redis 8.0+**

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

## API Usage

### Basic Usage

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

### ID Structure Parsing

```java
import top.baoding.snowflake.util.HexIdUtils;

long id = idGenerator.nextId();
System.out.println("Hex: " + HexIdUtils.toHex(id));
System.out.println("Binary: " + HexIdUtils.toBinary(id));
```

---

## Architecture Design

See [ARCHITECTURE.md](ARCHITECTURE.md)

---

## Deployment Guide

See [DEPLOYMENT.md](DEPLOYMENT.md)

---

## Comparison with Other Solutions

See [COMPARISON.md](COMPARISON.md)