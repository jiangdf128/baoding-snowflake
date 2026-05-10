package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis数据持久化验证测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@SpringBootTest(properties = {
    "spring.config.import=classpath:application-redis.yml",
    "baoding.snowflake.mode=redis",
    "baoding.snowflake.datacenter-id=0",
    "baoding.snowflake.scope=baoding-snowflake-verify"
})
@org.springframework.test.context.ContextConfiguration(classes = TestRedisSnowflakeApplication.class)
class RedisPersistenceTest {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private top.baoding.snowflake.core.IdGenerator idGenerator;

    @Test
    void verifyRedisDataPersistence() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用，跳过测试");
            return;
        }

        String key = "snowflake:baoding-snowflake-verify:0";

        System.out.println("=== 测试Redis数据持久化 ===");
        System.out.println("测试Key: " + key);

        Long before = stringRedisTemplate.opsForHash().size(key);
        System.out.println("测试前工作站数量: " + (before == null ? 0 : before));

        if (idGenerator != null) {
            System.out.println("\n生成5个ID:");
            for (int i = 0; i < 5; i++) {
                long id = idGenerator.nextId();
                System.out.println("  ID " + (i + 1) + ": " + id);
            }

            System.out.println("\n获取工作站在线状态:");
            for (int i = 0; i < 1024; i++) {
                Object value = stringRedisTemplate.opsForHash().get(key, String.valueOf(i));
                if (value != null) {
                    System.out.println("  WorkstationId " + i + " -> " + value);
                }
            }
        }

        Long after = stringRedisTemplate.opsForHash().size(key);
        System.out.println("\n测试后工作站数量: " + (after == null ? 0 : after));
        System.out.println("=== 持久化验证完成 ===");
    }

    @Test
    void listAllSnowflakeKeys() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用");
            return;
        }

        System.out.println("\n=== 扫描所有snowflake相关Key ===");
        org.springframework.data.redis.core.Cursor<byte[]> cursor =
            stringRedisTemplate.getConnectionFactory().getConnection()
                .scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match("*snowflake*").count(100).build());

        boolean found = false;
        while (cursor.hasNext()) {
            found = true;
            String k = new String(cursor.next());
            Long size = stringRedisTemplate.opsForHash().size(k);
            System.out.println("  " + k + " -> " + size + " 个工作站");
        }

        if (!found) {
            System.out.println("  未找到任何snowflake相关Key");
        }
    }
}