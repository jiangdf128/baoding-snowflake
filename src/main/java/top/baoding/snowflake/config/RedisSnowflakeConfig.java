package top.baoding.snowflake.config;

import top.baoding.snowflake.assembler.RedisSnowflakeAssembler;
import top.baoding.snowflake.core.IdGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis雪花ID生成器配置
 *
 * <p>
 * 配置基于Redis的雪花算法ID生成器，当 baoding.snowflake.mode=redis 时生效。
 * 要求Redis 8.0+。
 * </p>
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@ConditionalOnProperty(name = "baoding.snowflake.mode", havingValue = "redis")
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisSnowflakeConfig {

    @Bean
    public RedisSnowflakeAssembler redisSnowflakeAssembler(
            SnowflakeProperties snowflakeProperties,
            StringRedisTemplate stringRedisTemplate) {

        String instanceId = generateInstanceId();
        return new RedisSnowflakeAssembler(snowflakeProperties, stringRedisTemplate, instanceId);
    }

    @Bean
    public IdGenerator redisIdGenerator(RedisSnowflakeAssembler assembler) {
        return assembler.getIdGeneratorInstance();
    }

    /**
     * 生成实例ID（用于标识Redis中的工作站ID归属）
     */
    private String generateInstanceId() {
        return System.getProperty("hostname", "unknown") + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
    }
}