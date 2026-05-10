package top.baoding.snowflake.assembler;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import top.baoding.snowflake.config.SnowflakeProperties;
import top.baoding.snowflake.core.IdGenerator;
import top.baoding.snowflake.core.SnowflakeAssembler;
import top.baoding.snowflake.core.SnowflakeIdGenerator;
import top.baoding.snowflake.core.SnowflakeManager;
import top.baoding.snowflake.util.RedisFallbackHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis雪花ID装配器
 *
 * <p>
 * 负责在Redis模式下管理工作站ID的分配、续期、释放等生命周期。
 * 实现ApplicationListener接口监听Spring容器关闭事件，确保资源正确释放。
 * </p>
 *
 * <p>
 * Redis存储结构：
 * <ul>
 *   <li>Key格式：snowflake:{scope}:{datacenterId}</li>
 *   <li>Hash字段： workstationId -> instanceId（实例标识）</li>
 *   <li>Hash字段： workstationId:hb -> heartbeatTime（心跳时间）</li>
 *   <li>Hash字段： workstationId:h -> runHistory（运行记录）</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@Slf4j
public class RedisSnowflakeAssembler implements SnowflakeAssembler, ApplicationListener<ContextClosedEvent> {

    /**
     * Redis键前缀。
     */
    private static final String REDIS_KEY_PREFIX = "snowflake:";

    /**
     * 工作站ID的TTL（7天），用于自动清理僵尸工作站。
     */
    private static final long TTL_SECONDS = 7 * 24 * 60 * 60;

    /**
     * 最大工作站ID数量（1024个，10bit）。
     */
    private static final long MAX_WORKER_IDS = 1L << SnowflakeIdGenerator.WORKSTATION_ID_BITS_LENGTH;

    /**
     * 申请新工作站ID的Lua脚本。
     *
     * <p>
     * 脚本逻辑：
     * <ol>
     *   <li>遍历0~1023寻找第一个未占用的工作站ID</li>
     *   <li>如果找到，设置实例标识、心跳时间、TTL</li>
     *   <li>如果找不到，返回-1表示耗尽</li>
     * </ol>
     * </p>
     *
     * <p>
     * 参数：
     * <ul>
     *   <li>KEYS[1]: Redis Hash的key（格式：snowflake:{scope}:{datacenterId}）</li>
     *   <li>ARGV[1]: 实例标识（uuid）</li>
     *   <li>ARGV[2]: TTL秒数</li>
     *   <li>ARGV[3]: 最大工作站ID（1024）</li>
     *   <li>ARGV[4]: 心跳时间</li>
     * </ul>
     * </p>
     *
     * <p>
     * 返回值：
     * <ul>
     *   <li>>=0: 成功分配的工作站ID</li>
     *   <li>-1: 工作站ID耗尽</li>
     * </ul>
     * </p>
     */
    private static final String NEW_WORKER_ID_LUA_SCRIPT = """
            local key = KEYS[1]
            local uuid = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local max_id = tonumber(ARGV[3]) - 1
            local hb_time=ARGV[4]
            local worker_key

            for i = 0, max_id do
                worker_key = tostring(i)
                if redis.call('HEXISTS', key, worker_key) == 0 then
                    redis.call('HSET', key, worker_key, uuid)
                    redis.call('HSET', key, worker_key .. ':hb', hb_time)
                    redis.call('HEXPIRE', key, ttl, 'FIELDS', 1, worker_key)
                    return i
                end
            end
            return -1
            """;

    /**
     * 释放工作站ID的Lua脚本。
     *
     * <p>
     * 脚本逻辑：
     * <ol>
     *   <li>检查工作站ID是否存在</li>
     *   <li>检查实例标识是否匹配（防止误删其他实例的资源）</li>
     *   <li>如果匹配，删除工作站ID和心跳记录</li>
     * </ol>
     * </p>
     *
     * <p>
     * 参数：
     * <ul>
     *   <li>KEYS[1]: Redis Hash的key</li>
     *   <li>ARGV[1]: 工作站ID</li>
     *   <li>ARGV[2]: 期望的实例标识</li>
     * </ul>
     * </p>
     *
     * <p>
     * 返回值：
     * <ul>
     *   <li>1: 成功释放</li>
     *   <li>0: 失败（工作站ID不存在或实例标识不匹配）</li>
     * </ul>
     * </p>
     */
    private static final String RELEASE_WORKER_ID_LUA_SCRIPT = """
            local key = KEYS[1]
            local worker_id = ARGV[1]
            local expected_uuid = ARGV[2]
            if redis.call('HEXISTS', key, worker_id) == 1 then
                local current_uuid = redis.call('HGET', key, worker_id)
                if current_uuid == expected_uuid then
                    redis.call('HDEL', key, worker_id)
                    redis.call('HDEL', key, worker_id .. ':hb')
                    return 1
                else
                    return 0
                end
            else
                return 0
            end
            """;

    /**
     * 续期工作站ID租约的Lua脚本。
     *
     * <p>
     * 脚本逻辑：
     * <ol>
     *   <li>检查工作站ID是否存在</li>
     *   <li>如果存在，续期TTL</li>
     * </ol>
     * </p>
     *
     * <p>
     * 参数：
     * <ul>
     *   <li>KEYS[1]: Redis Hash的key</li>
     *   <li>ARGV[1]: 工作站ID</li>
     *   <li>ARGV[2]: TTL秒数</li>
     * </ul>
     * </p>
     */
    private static final String RENEW_LUA_SCRIPT = """
            local key = KEYS[1]
            local worker_id = ARGV[1]
            local ttl = tonumber(ARGV[2])
            if redis.call('HEXISTS', key, worker_id) == 1 then
                redis.call('HEXPIRE', key, ttl, 'FIELDS', 1, worker_id)
                return 1
            else
                return 0
            end
            """;

    /**
     * 雪花算法配置属性。
     */
    private final SnowflakeProperties snowflakeProperties;

    /**
     * Redis操作模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 实例标识（用于标识当前工作站在集群中的唯一性）。
     *
     * <p>
     * 格式：hostname-pid-nanoTime
     * 例如：baoding01-12345-9876543210
     * </p>
     */
    private final String instanceId;

    /**
     * 资源是否已释放的标志（原子操作确保只释放一次）。
     */
    @Getter
    private final AtomicBoolean cleaned = new AtomicBoolean(false);

    /**
     * 运行记录是否已保存的标志（原子操作确保只保存一次）。
     */
    private final AtomicBoolean saved = new AtomicBoolean(false);

    /**
     * ID生成器实例。
     */
    private volatile IdGenerator idGenerator;

    /**
     * 工作站ID（-1表示未分配）。
     */
    private long workerId = -1L;

    /**
     * 构造函数。
     *
     * <p>
     * 构造时立即注册到SnowflakeManager，以便JVM关闭时能够找到并释放资源。
     * </p>
     *
     * @param snowflakeProperties 雪花算法配置属性
     * @param stringRedisTemplate Redis操作模板
     * @param instanceId 实例标识
     */
    public RedisSnowflakeAssembler(SnowflakeProperties snowflakeProperties, StringRedisTemplate stringRedisTemplate, String instanceId) {
        this.snowflakeProperties = snowflakeProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceId = instanceId;
        SnowflakeManager.register(this);
    }

    /**
     * 获取雪花算法的Scope（应用范围）。
     *
     * <p>
     * 如果配置中设置了scope则使用配置值，否则使用默认值"default"。
     * Scope用于在Redis中隔离不同应用或环境的 workstation ID分配。
     * </p>
     *
     * @return 雪花算法的作用域
     */
    private String getSnowflakeScope() {
        return StringUtils.hasText(snowflakeProperties.getScope()) ? snowflakeProperties.getScope() : "default";
    }

    /**
     * 获取雪花算法在Redis中的Hash键。
     *
     * <p>
     * 格式：snowflake:{scope}:{datacenterId}
     * 例如：snowflake:baoding-order:0
     * </p>
     *
     * @return Redis Hash的键
     */
    private String getSnowflakeRedisKey() {
        return REDIS_KEY_PREFIX + this.getSnowflakeScope().trim() + ":" + snowflakeProperties.getDatacenterId();
    }

    /**
     * 获取工作站ID（如未分配则从Redis申请）。
     *
     * <p>
     * 工作站ID分配流程：
     * <ol>
     *   <li>检查本地缓存是否有工作站ID</li>
     *   <li>如果无缓存，通过Lua脚本在Redis中原子性申请</li>
     *   <li>申请成功返回工作站ID，失败抛出异常</li>
     * </ol>
     * </p>
     *
     * <p>
     * 分配策略：
     * <ul>
     *   <li>优先分配最小的可用工作站ID</li>
     *   <li>每个工作站ID有7天TTL（心跳续期）</li>
     *   <li>超过7天无心跳的工作站ID会被自动回收</li>
     * </ul>
     * </p>
     *
     * @return 已分配的工作站ID（0~1023）
     * @throws RuntimeException 如果工作站ID耗尽
     */
    private long getWorkerId() {
        if (this.workerId == -1) {
            Long id = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(NEW_WORKER_ID_LUA_SCRIPT, Long.class),
                    List.of(this.getSnowflakeRedisKey()),
                    instanceId,
                    String.valueOf(TTL_SECONDS),
                    String.valueOf(MAX_WORKER_IDS),
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            if (id != null && id >= 0) {
                this.workerId = id;
                log.info("[Snowflake] 已成功分配工作站ID {} 给Scope {} ", this.workerId, this.getSnowflakeScope());
            } else {
                throw new RuntimeException(String.format("雪花算法Scope为%s的工作站ID已耗尽，请核查或更改数据中心ID...", this.getSnowflakeScope()));
            }
        }
        return this.workerId;
    }

    /**
     * 续期工作站ID的租约（每天执行一次）。
     *
     * <p>
     * 租约续期机制：
     * <ul>
     *   <li>初始延迟：启动1小时后开始执行</li>
     *   <li>执行周期：每24小时执行一次</li>
     *   <li>续期长度：7天</li>
     * </ul>
     * </p>
     *
     * <p>
     * 注意事项：
     * <ul>
     *   <li>心跳（heartbeat）每3分钟执行一次，更新心跳时间</li>
     *   <li>租约续期每天执行一次，延长TTL到7天</li>
     *   <li>如果续期失败，会记录错误日志但不影响业务</li>
     * </ul>
     * </p>
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000L, initialDelay = 60 * 60 * 1000L)
    private void renewLease() {
        try {
            if (this.workerId >= 0) {
                stringRedisTemplate.execute(
                        new DefaultRedisScript<>(RENEW_LUA_SCRIPT, Long.class),
                        List.of(this.getSnowflakeRedisKey()),
                        String.valueOf(this.workerId),
                        String.valueOf(TTL_SECONDS)
                );
                log.debug("[Snowflake] 已续期工作站ID {} 的租约", this.workerId);
            }
        } catch (Exception e) {
            log.error("[Snowflake] 续期工作站ID {} 租约失败", this.workerId, e);
        }
    }

    /**
     * 更新工作站ID的心跳时间（每3分钟执行一次）。
     *
     * <p>
     * 心跳机制作用：
     * <ul>
     *   <li>表明工作站ID处于活跃状态</li>
     *   <li>配合7天TTL实现自动清理僵尸工作站</li>
     *   <li>运维人员可据此判断工作站是否存活</li>
     * </ul>
     * </p>
     *
     * <p>
     * 心跳更新字段：workstationId:hb
     * </p>
     */
    @Scheduled(fixedRate = 3 * 60 * 1000L, initialDelay = 30 * 1000L)
    private void heartbeat() {
        try {
            if (this.workerId >= 0) {
                String itemKey = this.workerId + ":hb";
                stringRedisTemplate.opsForHash().put(
                        this.getSnowflakeRedisKey(),
                        itemKey,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                );
                log.debug("[Snowflake] 已更新工作站ID {} 的心跳时间", this.workerId);
            }
        } catch (Exception e) {
            log.error("[Snowflake] 更新工作站ID {} 心跳失败", this.workerId, e);
        }
    }

    /**
     * 释放工作站ID资源。
     *
     * <p>
     * 释放流程：
     * <ol>
     *   <li>使用原子操作确保只释放一次</li>
     *   <li>优先使用标准Spring Data Redis执行Lua脚本释放</li>
     *   <li>如果标准方案失败，尝试兜底方案（直接使用Lettuce原生客户端）</li>
     *   <li>兜底方案最后必须关闭连接</li>
     * </ol>
     * </p>
     *
     * <p>
     * 兜底方案说明：
     * <ul>
     *   <li>当Spring的LettuceConnectionFactory已停止时触发</li>
     *   <li>使用独立的Redis连接执行释放脚本</li>
     *   <li>执行完成后必须关闭独立连接，避免资源泄漏</li>
     * </ul>
     * </p>
     *
     * <p>
     * 释放安全性：
     * <ul>
     *   <li>通过Lua脚本验证实例标识匹配才能释放</li>
     *   <li>防止误删其他实例的工作站ID</li>
     * </ul>
     * </p>
     */
    @Override
    public void releaseWorkerId() {
        if (cleaned.compareAndSet(false, true)) {
            try {
                Long id = stringRedisTemplate.execute(
                        new DefaultRedisScript<>(RELEASE_WORKER_ID_LUA_SCRIPT, Long.class),
                        List.of(this.getSnowflakeRedisKey()),
                        String.valueOf(this.workerId),
                        instanceId
                );
                if (id != null && id >= 0) {
                    log.info("[Snowflake] 已成功释放工作站ID {} 资源", this.workerId);
                }
            } catch (Exception e) {
                log.error("[Snowflake] 释放工作站ID {} 失败，尝试执行兜底方案...", this.workerId, e);
                try {
                    Long result = RedisFallbackHelper.eval(
                            (LettuceConnectionFactory) stringRedisTemplate.getConnectionFactory(),
                            RELEASE_WORKER_ID_LUA_SCRIPT,
                            new String[]{this.getSnowflakeRedisKey()},
                            String.valueOf(this.workerId),
                            instanceId
                    );
                    if (result != null && result >= 0) {
                        System.err.printf("[资源回收] 兜底方案，已成功释放工作站ID [%d] 资源%n", this.workerId);
                    }
                } catch (Exception e1) {
                    System.err.println("执行兜底方案又碰到异常错误，因此已放弃释放WorkerId资源，可能需要人工介入处理..." + e1);
                } finally {
                    RedisFallbackHelper.shutdown();
                }
            }
        }
    }

    /**
     * 获取工作站ID的上次运行记录。
     *
     * <p>
     * 运行记录格式：lastTimestamp|sequence|id|datetime|binary
     * 例如：1743079365142|1|76957782447792129|2025-03-27T20:42:45.142|100010001011010001011001000000101100000001010000000000001
     * </p>
     *
     * <p>
     * 字段说明：
     * <ul>
     *   <li>lastTimestamp: 上次生成ID时的时间戳（8ms单位）</li>
     *   <li>sequence: 上次的序列号</li>
     *   <li>id: 上次生成的ID</li>
     *   <li>datetime: 格式化的时间</li>
     *   <li>binary: ID的二进制表示</li>
     * </ul>
     * </p>
     *
     * @return 运行记录字符串，如果无记录则返回空字符串
     */
    @Override
    public String getLastRunHistory() {
        if (this.workerId < 0) {
            return "";
        }
        String itemKey = this.workerId + ":h";
        Object o = stringRedisTemplate.opsForHash().get(this.getSnowflakeRedisKey(), itemKey);
        if (o != null) {
            return o.toString();
        }
        return "";
    }

    /**
     * 保存运行记录到Redis。
     *
     * <p>
     * 保存流程：
     * <ol>
     *   <li>使用原子操作确保只保存一次</li>
     *   <li>优先使用标准Spring Data Redis操作保存</li>
     *   <li>如果标准方案失败，尝试兜底方案（直接使用Lettuce原生客户端）</li>
     * </ol>
     * </p>
     *
     * <p>
     * 保存内容：
     * <ul>
     *   <li>Key: snowflake:{scope}:{datacenterId}</li>
     *   <li>Field: {workstationId}:h</li>
     *   <li>Value: lastTimestamp|sequence|id|datetime|binary</li>
     * </ul>
     * </p>
     *
     * <p>
     * 兜底方案说明：
     * <ul>
     *   <li>当Spring的LettuceConnectionFactory已停止时触发</li>
     *   <li>使用独立的Redis连接执行HSET命令</li>
     *   <li>注意：这里不调用RedisFallbackHelper.shutdown()，因为后续还要调用releaseWorkerId</li>
     * </ul>
     * </p>
     *
     * @param runHistory 运行记录字符串
     */
    @Override
    public void saveRunHistory(String runHistory) {
        if (saved.compareAndSet(false, true)) {
            String redisKey = this.getSnowflakeRedisKey();
            String itemKey = this.workerId + ":h";
            try {
                stringRedisTemplate.opsForHash().put(redisKey, itemKey, runHistory);
                log.info("[Snowflake] 已保存运行参数 {}", runHistory);
            } catch (Exception e) {
                log.error("[Snowflake] 标准保存运行参数失败，启动兜底方案...", e);
                try {
                    RedisFallbackHelper.eval(
                            (LettuceConnectionFactory) stringRedisTemplate.getConnectionFactory(),
                            "return redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])",
                            new String[]{redisKey},
                            itemKey,
                            runHistory
                    );
                    System.err.println("[资源回收] 运行记录已通过兜底方案成功保存：" + runHistory);
                } catch (Exception e1) {
                    System.err.println("[资源回收] 致命错误：保存运行记录兜底方案也执行失败！");
                }
            }
        }
    }

    /**
     * 检查资源是否已释放。
     *
     * @return true表示已释放，false表示未释放
     */
    @Override
    public boolean isReleased() {
        return cleaned.get();
    }

    /**
     * 获取ID生成器实例。
     *
     * @return IdGenerator实例，如果未创建则返回null
     */
    @Override
    public IdGenerator getIdGenerator() {
        return this.idGenerator;
    }

    /**
     * 设置ID生成器实例。
     *
     * @param idGenerator ID生成器实例
     */
    @Override
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 获取ID生成器实例（如未创建则创建）。
     *
     * <p>
     * 双重检查锁定模式创建IdGenerator实例：
     * <ol>
     *   <li>检查实例是否为空</li>
     *   <li>加锁</li>
     *   <li>再次检查并创建实例</li>
     * </ol>
     * </p>
     *
     * <p>
     * 创建流程：
     * <ol>
     *   <li>校验数据中心ID是否在有效范围（0~7）</li>
     *   <li>分配工作站ID</li>
     *   <li>创建SnowflakeIdGenerator实例</li>
     *   <li>调用init()初始化</li>
     * </ol>
     * </p>
     *
     * @return IdGenerator实例
     * @throws IllegalArgumentException 如果数据中心ID超出有效范围
     */
    public IdGenerator getIdGeneratorInstance() {
        if (this.idGenerator == null) {
            synchronized (this) {
                if (this.idGenerator == null) {
                    long maxDatacenterId = ~(-1L << SnowflakeIdGenerator.DATACENTER_ID_BITS_LENGTH);
                    if (snowflakeProperties.getDatacenterId() > maxDatacenterId || snowflakeProperties.getDatacenterId() < 0) {
                        throw new IllegalArgumentException(String.format("数据中心ID不能大于%d或小于0，当前值为%d", maxDatacenterId, snowflakeProperties.getDatacenterId()));
                    }
                    SnowflakeIdGenerator snowflakeGenerator = new SnowflakeIdGenerator(
                            snowflakeProperties.getDatacenterId(),
                            this.getWorkerId(),
                            this,
                            snowflakeProperties
                    );
                    snowflakeGenerator.init();
                    this.idGenerator = snowflakeGenerator;
                }
            }
        }
        return this.idGenerator;
    }

    /**
     * 处理Spring容器关闭事件。
     *
     * <p>
     * 此方法在Spring应用关闭时被调用，确保：
     * <ol>
     *   <li>如果IdGenerator已创建，调用其saveAndRelease()保存状态并释放资源</li>
     *   <li>如果IdGenerator未创建，直接释放工作站ID</li>
     * </ol>
     * </p>
     *
     * <p>
     * 执行顺序：
     * <ol>
     *   <li>先保存运行记录（lastTimestamp和sequence）</li>
     *   <li>再释放工作站ID</li>
     * </ol>
     * </p>
     *
     * <p>
     * 异常处理：
     * <ul>
     *   <li>捕获所有异常避免影响其他关闭流程</li>
     *   <li>记录错误日志便于排查</li>
     * </ul>
     * </p>
     *
     * @param event Spring上下文关闭事件
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            if (this.idGenerator != null) {
                this.idGenerator.saveAndRelease();
            } else {
                this.releaseWorkerId();
            }
        } catch (Exception e) {
            log.error("[Snowflake] 释放资源时碰到异常", e);
        }
    }
}