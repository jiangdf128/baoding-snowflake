# 部署指南 / Deployment Guide

## 一、Local模式部署 / Local Mode Deployment

### 1.1 Maven依赖 / Maven Dependency

```xml
<dependency>
    <groupId>io.github.jiangdf128</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.2 配置示例 / Configuration Example

```yaml
baoding:
  snowflake:
    mode: local
    datacenter-id: 0
    workstation-id: 0
  data-dir:
    path: /tmp/snowflake-data
```

### 1.3 使用示例 / Usage Example

```java
@Autowired
private IdGenerator idGenerator;

public void generateIds() {
    long id = idGenerator.nextId();
    System.out.println("Generated ID: " + id);
}
```

---

## 二、Redis模式部署 / Redis Mode Deployment

### 2.1 前置要求 / Prerequisites

- Redis 8.0+ 已安装并运行
- Spring Boot应用

### 2.2 Maven依赖 / Maven Dependency

```xml
<dependency>
    <groupId>io.github.jiangdf128</groupId>
    <artifactId>baoding-snowflake</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2.3 配置示例 / Configuration Example

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

### 2.4 配置说明 / Configuration Details

| 配置项 / Config | 说明 / Description |
|----------------|-------------------|
| `mode` | 运行模式：local 或 redis |
| `datacenter-id` | 数据中心ID，范围0~7 |
| `scope` | 作用域，用于Redis键前缀，默认使用微服务名 |
| `workstation-id` | 仅Local模式需要手动配置 |
| `max-time-back-mills` | 允许时间回拨最大值（毫秒） |
| `time-back-warn-loop-count` | 时间回拨警告频率 |

---

## 三、K8s部署示例 / K8s Deployment Example

### 3.1 Deployment配置 / Deployment Config

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-service
  template:
    metadata:
      labels:
        app: my-service
    spec:
      containers:
      - name: my-service
        image: my-service:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_APPLICATION_NAME
          value: "my-service"
        - name: BAODING_SNOWFLAKE_MODE
          value: "redis"
        - name: BAODING_SNOWFLAKE_DATACENTER_ID
          value: "0"
        - name: SPRING_DATA_REDIS_HOST
          value: "redis-service"
        - name: SPRING_DATA_REDIS_PORT
          value: "6379"
```

### 3.2 Redis Service配置 / Redis Service Config

```yaml
apiVersion: v1
kind: Service
metadata:
  name: redis-service
spec:
  ports:
  - port: 6379
    targetPort: 6379
  selector:
    app: redis
```

---

## 四、Docker部署 / Docker Deployment

### 4.1 构建镜像 / Build Image

```dockerfile
FROM eclipse-temurin:25-jdk-alpine
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 4.2 运行容器 / Run Container

```bash
docker run -e BAODING_SNOWFLAKE_MODE=redis \
           -e BAODING_SNOWFLAKE_DATACENTER_ID=0 \
           -e SPRING_DATA_REDIS_HOST=redis-host \
           my-service:1.0.0
```

---

## 五、监控与运维 / Monitoring & Operations

### 5.1 Redis键结构 / Redis Key Structure

```
snowflake:{scope}:{datacenterId}  # Hash类型
  └── {workerId}       -> instanceId（实例标识）
  └── {workerId}:hb    -> heartbeat时间
  └── {workerId}:h     -> lastRunHistory
```

### 5.2 健康检查 / Health Check

```java
@Autowired
private IdGenerator idGenerator;

@GetMapping("/health")
public Map<String, Object> health() {
    Map<String, Object> result = new HashMap<>();
    result.put("workerId", idGenerator.getWorkstationId());
    result.put("datacenterId", idGenerator.getDataCenterId());
    result.put("status", "UP");
    return result;
}
```

### 5.3 日志输出 / Log Output

Redis模式下关键日志：
- `[Snowflake] Successfully allocated workerId: X` - 启动时分配
- `[Snowflake] Heartbeat updated for workerId: X` - 心跳更新
- `[Snowflake] Successfully released workerId: X` - 停止时释放