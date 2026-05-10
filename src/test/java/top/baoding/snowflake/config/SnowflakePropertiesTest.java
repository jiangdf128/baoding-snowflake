package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnowflakeProperties配置测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class SnowflakePropertiesTest {

    @Test
    void testDefaultValues() {
        SnowflakeProperties props = new SnowflakeProperties();
        assertEquals("redis", props.getMode());
        assertEquals(0, props.getDatacenterId());
        assertEquals(0, props.getWorkstationId());
        assertEquals(86400000, props.getMaxTimeBackMills());
        assertEquals(5000, props.getTimeBackWarnLoopCount());
    }

    @Test
    void testSetterGetter() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setMode("local");
        props.setDatacenterId(5);
        props.setWorkstationId(100);
        props.setMaxTimeBackMills(1000);
        props.setTimeBackWarnLoopCount(100);

        assertEquals("local", props.getMode());
        assertEquals(5, props.getDatacenterId());
        assertEquals(100, props.getWorkstationId());
        assertEquals(1000, props.getMaxTimeBackMills());
        assertEquals(100, props.getTimeBackWarnLoopCount());
    }

    @Test
    void testScopeConfiguration() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setScope("my-service");

        assertEquals("my-service", props.getScope());
    }

    @Test
    void testValidDatacenterIdValues() {
        SnowflakeProperties props = new SnowflakeProperties();
        for (long i = 0; i <= 7; i++) {
            props.setDatacenterId(i);
            assertEquals(i, props.getDatacenterId());
        }
    }

    @Test
    void testValidWorkstationIdValues() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setWorkstationId(0);
        assertEquals(0, props.getWorkstationId());

        props.setWorkstationId(1023);
        assertEquals(1023, props.getWorkstationId());
    }
}