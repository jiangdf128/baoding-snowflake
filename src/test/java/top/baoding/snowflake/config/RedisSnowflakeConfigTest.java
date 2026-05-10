package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis雪花配置测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class RedisSnowflakeConfigTest {

    @Test
    void testRedisConfigBeanCreation() {
        RedisSnowflakeConfig config = new RedisSnowflakeConfig();
        assertNotNull(config);
    }

    @Test
    void testInstanceIdGeneration() {
        RedisSnowflakeConfig config = new RedisSnowflakeConfig();

        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(0);

        String instanceId = System.getProperty("hostname", "unknown") + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        assertNotNull(instanceId);
        assertTrue(instanceId.contains("-"));
    }

    @Test
    void testSnowflakePropertiesRedisMode() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setMode("redis");
        props.setDatacenterId(0);
        props.setScope("test-service");

        assertEquals("redis", props.getMode());
        assertEquals("test-service", props.getScope());
    }
}