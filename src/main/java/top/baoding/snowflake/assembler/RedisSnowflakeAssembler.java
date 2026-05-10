package top.baoding.snowflake.assembler;

import top.baoding.snowflake.config.SnowflakeProperties;
import top.baoding.snowflake.core.IdGenerator;
import top.baoding.snowflake.core.SnowflakeAssembler;
import top.baoding.snowflake.core.SnowflakeIdGenerator;
import top.baoding.snowflake.core.SnowflakeManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis雪花ID装配器
 * <p>
 * 基于Redis自动分配/释放工作站ID，支持K8s云原生环境。
 * 要求Redis 8.0+。
 * </p>
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@Slf4j
public class RedisSnowflakeAssembler implements SnowflakeAssembler {

    private static final String REDIS_KEY_PREFIX = "snowflake:";
    private static final long TTL_SECONDS = 7 * 24 * 60 * 60; // 7天
    private static final long MAX_WORKER_IDS = 1L << SnowflakeIdGenerator.WORKSTATION_ID_BITS_LENGTH;

    // 新申请WorkerId的Lua脚本
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

    // 释放WorkerId脚本
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

    // 续期Lua脚本
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

    private final SnowflakeProperties snowflakeProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final String instanceId;

    @Getter
    private final AtomicBoolean cleaned = new AtomicBoolean(false);
    private final AtomicBoolean saved = new AtomicBoolean(false);

    private volatile IdGenerator idGenerator;
    private long workerId = -1L;

    public RedisSnowflakeAssembler(SnowflakeProperties snowflakeProperties, StringRedisTemplate stringRedisTemplate, String instanceId) {
        this.snowflakeProperties = snowflakeProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceId = instanceId;
        SnowflakeManager.register(this);
    }

    private String getSnowflakeScope() {
        return StringUtils.hasText(snowflakeProperties.getScope()) ? snowflakeProperties.getScope() : "default";
    }

    private String getSnowflakeRedisKey() {
        return REDIS_KEY_PREFIX + this.getSnowflakeScope().trim() + ":" + snowflakeProperties.getDatacenterId();
    }

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
                log.info("[Snowflake] Successfully allocated workerId: {} for scope: {}", this.workerId, this.getSnowflakeScope());
            } else {
                throw new RuntimeException(String.format("雪花算法Scope为%s的工作站ID已耗尽，请核查或更改数据中心ID...", this.getSnowflakeScope()));
            }
        }
        return this.workerId;
    }

    /**
     * 心跳续期（每24小时执行一次，初始延迟1小时）
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
                log.debug("[Snowflake] Renewed lease for workerId: {}", this.workerId);
            }
        } catch (Exception e) {
            log.error("[Snowflake] Failed to renew lease for workerId: {}", this.workerId, e);
        }
    }

    /**
     * 心跳（每3分钟执行一次，初始延迟30秒）
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
                log.debug("[Snowflake] Heartbeat updated for workerId: {}", this.workerId);
            }
        } catch (Exception e) {
            log.error("[Snowflake] Failed to update heartbeat for workerId: {}", this.workerId, e);
        }
    }

    @Override
    public void releaseWorkerId() {
        if (cleaned.compareAndSet(false, true)) {
            try {
                stringRedisTemplate.execute(
                        new DefaultRedisScript<>(RELEASE_WORKER_ID_LUA_SCRIPT, Long.class),
                        List.of(this.getSnowflakeRedisKey()),
                        String.valueOf(this.workerId),
                        instanceId
                );
                log.info("[Snowflake] Successfully released workerId: {}", this.workerId);
            } catch (Exception e) {
                log.error("[Snowflake] Failed to release workerId: {}", this.workerId, e);
            }
        }
    }

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

    @Override
    public void saveRunHistory(String runHistory) {
        if (saved.compareAndSet(false, true)) {
            String redisKey = this.getSnowflakeRedisKey();
            String itemKey = this.workerId + ":h";
            try {
                stringRedisTemplate.opsForHash().put(redisKey, itemKey, runHistory);
                log.info("[Snowflake] Saved run history: {}", runHistory);
            } catch (Exception e) {
                log.error("[Snowflake] Failed to save run history", e);
            }
        }
    }

    @Override
    public boolean isReleased() {
        return cleaned.get();
    }

    @Override
    public IdGenerator getIdGenerator() {
        return this.idGenerator;
    }

    @Override
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 获取IdGenerator实例（懒加载）
     */
    public IdGenerator getIdGeneratorInstance() {
        if (this.idGenerator == null) {
            synchronized (this) {
                if (this.idGenerator == null) {
                    long maxDatacenterId = ~(-1L << SnowflakeIdGenerator.DATACENTER_ID_BITS_LENGTH);
                    if (snowflakeProperties.getDatacenterId() > maxDatacenterId || snowflakeProperties.getDatacenterId() < 0) {
                        throw new IllegalArgumentException(String.format("数据中心ID不能大于%d或小于0，当前值为%d", maxDatacenterId, snowflakeProperties.getDatacenterId()));
                    }
                    this.idGenerator = new SnowflakeIdGenerator(
                            snowflakeProperties.getDatacenterId(),
                            this.getWorkerId(),
                            this,
                            snowflakeProperties
                    );
                    this.idGenerator.init();
                }
            }
        }
        return this.idGenerator;
    }
}