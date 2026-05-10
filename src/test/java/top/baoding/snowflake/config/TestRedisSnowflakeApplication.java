package top.baoding.snowflake.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Redis雪花算法测试用SpringBoot应用
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(SnowflakeProperties.class)
public class TestRedisSnowflakeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestRedisSnowflakeApplication.class, args);
    }
}
