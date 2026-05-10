package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis数据查看测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@SpringBootTest(properties = {
    "spring.config.import=classpath:application-redis.yml"
})
@org.springframework.test.context.ContextConfiguration(classes = TestRedisSnowflakeApplication.class)
class RedisDataViewerTest {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void viewRedisData() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用");
            return;
        }

        String key = "snowflake:baoding-snowflake-test:0";
        System.out.println("=== Redis 雪花算法数据 ===");
        System.out.println("Key: " + key);

        Long size = stringRedisTemplate.opsForHash().size(key);
        System.out.println("已分配工作站数量: " + size);

        if (size != null && size > 0) {
            System.out.println("\n工作站在线列表:");
            for (int i = 0; i < 1024; i++) {
                Object workstationId = stringRedisTemplate.opsForHash().get(key, String.valueOf(i));
                if (workstationId != null) {
                    System.out.println("  WorkstationId " + i + ": " + workstationId);
                }
            }
        }

        System.out.println("\n=== 测试完成 ===");
    }

    @Test
    void checkAllScopes() {
        if (stringRedisTemplate == null) {
            System.out.println("Redis未可用");
            return;
        }

        System.out.println("=== 查找所有雪花算法相关Key ===");
        ScanOptions options = ScanOptions.scanOptions().match("*snowflake*").count(100).build();
        Cursor<byte[]> cursor = stringRedisTemplate.getConnectionFactory().getConnection().scan(options);
        while (cursor.hasNext()) {
            System.out.println("Found key: " + new String(cursor.next()));
        }

        System.out.println("\n=== 检查 baoding-snowflake-test 的数据 ===");
        String testKey = "snowflake:baoding-snowflake-test:0";
        Long testSize = stringRedisTemplate.opsForHash().size(testKey);
        System.out.println("baoding-snowflake-test 已分配工作站数: " + testSize);
    }
}