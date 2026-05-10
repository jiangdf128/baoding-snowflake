package top.baoding.snowflake.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Redis数据查看器测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class RedisDataViewerAppTest {

    @Test
    void viewRedisData() {
        System.out.println("\n========================================");
        System.out.println("       Redis 雪花算法数据查看器");
        System.out.println("========================================\n");

        try (ConfigurableApplicationContext ctx = SpringApplication.run(
                RedisDataViewerApp.class,
                "--spring.config.import=classpath:application-redis.yml",
                "--baoding.snowflake.mode=redis",
                "--baoding.snowflake.datacenter-id=0",
                "--baoding.snowflake.scope=baoding-snowflake-test")) {

            System.out.println("应用已启动，数据查看完成");
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
        }
    }
}