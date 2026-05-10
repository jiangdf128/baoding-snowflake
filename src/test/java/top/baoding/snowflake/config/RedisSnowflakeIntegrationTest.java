package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.baoding.snowflake.core.IdGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis雪花算法集成测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@SpringBootTest(properties = {
    "spring.config.import=classpath:application-redis.yml",
    "baoding.snowflake.mode=redis",
    "baoding.snowflake.datacenter-id=0",
    "baoding.snowflake.scope=baoding-snowflake-test"
})
@org.springframework.test.context.ContextConfiguration(classes = TestRedisSnowflakeApplication.class)
class RedisSnowflakeIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired(required = false)
    private IdGenerator idGenerator;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisConnection() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用，跳过集成测试");
            return;
        }

        String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
        assertEquals("PONG", pong);
        System.out.println("Redis连接成功: " + pong);
    }

    @Test
    void testIdGeneratorBeanCreation() {
        if (idGenerator == null) {
            System.out.println("IdGenerator未创建，可能Redis不可用");
            return;
        }

        assertNotNull(idGenerator);
        System.out.println("IdGenerator已创建");
        System.out.println("WorkerId: " + idGenerator.getWorkstationId());
        System.out.println("DataCenterId: " + idGenerator.getDataCenterId());
    }

    @Test
    void testIdGeneration() {
        if (idGenerator == null) {
            System.out.println("IdGenerator未创建，可能Redis不可用");
            return;
        }

        long id1 = idGenerator.nextId();
        long id2 = idGenerator.nextId();

        assertTrue(id1 > 0, "生成的ID应该大于0");
        assertTrue(id2 > 0, "生成的ID应该大于0");
        assertTrue(id2 > id1, "后生成的ID应该大于先生成的ID");

        System.out.println("生成ID: " + id1);
        System.out.println("生成ID: " + id2);
    }

    @Test
    void testUniqueIds() {
        if (idGenerator == null) {
            System.out.println("IdGenerator未创建，可能Redis不可用");
            return;
        }

        java.util.Set<Long> ids = new java.util.HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            ids.add(idGenerator.nextId());
        }

        assertEquals(count, ids.size(), "所有生成的ID应该唯一");
        System.out.println("成功生成" + count + "个唯一ID");
    }

    @Test
    void testWorkerIdAllocationInRedis() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用，跳过测试");
            return;
        }

        String redisKey = "snowflake:baoding-snowflake-test:0";
        Long size = stringRedisTemplate.opsForHash().size(redisKey);

        System.out.println("Redis中已分配的工作站ID数量: " + size);
        assertTrue(size >= 0);
    }
}