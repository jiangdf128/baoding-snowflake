# 架构设计文档 / Architecture Design Document

## 一、ID结构 / ID Structure

### 1.1 64位ID组成 / 64-bit ID Composition

```
+------------------------------------------------------------------+
| 41bit 时间戳 (相对DD_EPOCH) | 3bit 数据中心ID | 10bit 工作站ID | 12bit 序列号 |
| 41bit Timestamp (relative to DD_EPOCH) | 3bit DataCenterID | 10bit WorkerID | 12bit Sequence |
+------------------------------------------------------------------+
```

### 1.2 核心参数 / Core Parameters

| 参数 / Parameter | 值 / Value | 说明 / Description |
|-----------------|-----------|-------------------|
| 时间窗口 / Time Window | 8ms | 每8ms可产生最多4096个ID / Max 4096 IDs per 8ms |
| 序列号 / Sequence | 12bit | 范围0~4095 / Range 0~4095 |
| 工作站ID / WorkerID | 10bit | 范围0~1023，共1024个 / Range 0~1023, total 1024 |
| 数据中心ID / DataCenterID | 3bit | 范围0~7，共8个 / Range 0~7, total 8 |
| 最大实例数 / Max Instances | 8192 | 1024 × 8 |

### 1.3 产能估算 / Capacity Estimation

```
单实例 / Single Instance: 4096 / 0.008 = 51.2万ID/秒 / 512,000 IDs/sec
横向扩展 / Horizontal: 8192 × 51.2万 = 5.2亿ID/秒 / 5.2 billion IDs/sec
```

---

## 二、核心原理 / Core Principles

### 2.1 时间窗口机制 / Time Window Mechanism

```java
private static final long EVERY_FEW_MILLISECOND = 8;

protected long currentTimeEveryFewMillis() {
    return System.currentTimeMillis() / EVERY_FEW_MILLISECOND;
}
```

选择8ms而非业界常用的1ms原因：
- 单实例QPS提升4倍（51.2万 vs 12万）
- ID寿命延长至约138年

Reason for choosing 8ms instead of industry-standard 1ms:
- Single instance QPS increased 4x (512k vs 120k)
- ID lifespan extended to ~138 years

### 2.2 时间回拨处理 / Clock Backward Handling

```java
if (timestamp < lastTimestamp) {
    diffTimeBack = lastTimestamp - timestamp;
    if (diffTimeBack > maxTimeBackThreshold) {
        throw new RuntimeException("时钟回拨超过阈值");
    }
    // 序号未耗尽则复用上次时间戳，耗尽则递增到下一时间窗口
    // If sequence not exhausted, reuse last timestamp; otherwise increment to next window
    if (((sequence + 1) & sequenceMask) == 0) {
        timestamp = lastTimestamp + 1;
    } else {
        timestamp = lastTimestamp;
    }
}
```

核心思想：宁可"追回"也不拒绝服务
Core idea: Catch up rather than reject service

### 2.3 线程安全 / Thread Safety

使用`synchronized`保证线程安全：
- synchronized锁开销极小（纯CPU计算，无任何等待）
- 获取/释放锁约100ns
- 实际吞吐可达200~500万次/秒

Using `synchronized` for thread safety:
- Lock overhead is minimal (pure CPU computation, no waiting)
- Lock acquisition/release ~100ns
- Actual throughput can reach 2-5 million/sec

---

## 三、运行模式 / Running Modes

### 3.1 Local模式 / Local Mode

适用场景：开发环境、单机部署
Use case: Development, single-machine deployment

特点：
- 状态存储在本地文件
- 需要手动配置workstationId
- 无需额外依赖

Features:
- State stored in local file
- Manual workstationId configuration required
- No additional dependencies

### 3.2 Redis模式 / Redis Mode

适用场景：生产环境、K8s云原生环境
Use case: Production, K8s cloud-native environments

特点：
- Redis自动分配/释放workstationId
- 心跳续期（3分钟心跳，24小时续期）
- Pod启停自动化工夫

Features:
- Redis auto-allocation/release of workstationId
- Heartbeat renewal (3min heartbeat, 24hr renewal)
- Automatic Pod start/stop management

---

## 四、关键设计 / Key Design

### 4.1 JVM退出钩子 / JVM Shutdown Hook

```java
static {
    Runtime.getRuntime().addShutdownHook(
        new Thread(SnowflakeManager::doRelease, "Snowflake-Cleanup-Hook")
    );
}
```

确保应用退出时正确保存状态并释放资源。
Ensures proper state saving and resource release on application exit.

### 4.2 Redis Lua脚本原子操作 / Redis Lua Script Atomic Operations

WorkerId分配（原子操作）：
```lua
for i = 0, max_id do
    if redis.call('HEXISTS', key, worker_key) == 0 then
        redis.call('HSET', key, worker_key, uuid)
        return i
    end
end
return -1
```

### 4.3 渐进式时间补偿 / Gradual Time Compensation

时间回拨时：
- 序列号未耗尽：复用上次时间戳
- 序列号耗尽：递增到下一时间窗口

On clock backward:
- Sequence not exhausted: reuse last timestamp
- Sequence exhausted: increment to next time window

---

## 五、依赖关系 / Dependencies

| 组件 / Component | 版本 / Version | 说明 / Description |
|-----------------|---------------|-------------------|
| Spring Boot | 4.0+ | 自动配置 / Auto-configuration |
| Redis | 8.0+ | 仅Redis模式需要 / Redis mode only |
| Lombok | 1.18+ | 注解处理 / Annotation processing |