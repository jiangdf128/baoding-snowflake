package top.baoding.snowflake.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis数据查看器
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(SnowflakeProperties.class)
public class RedisDataViewerApp {

    public static void main(String[] args) {
        SpringApplication.run(RedisDataViewerApp.class, args);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    DataViewerRunner dataViewerRunner(StringRedisTemplate template) {
        return new DataViewerRunner(template);
    }

    static class DataViewerRunner implements Runnable {
        private final StringRedisTemplate template;

        DataViewerRunner(StringRedisTemplate template) {
            this.template = template;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("\n========================================");
            System.out.println("       Redis 雪花算法数据查看器");
            System.out.println("========================================\n");

            System.out.println("=== 扫描所有 snowflake 相关Key ===");
            try {
                ScanOptions options = ScanOptions.scanOptions().match("*snowflake*").count(100).build();
                Cursor<byte[]> cursor = template.getConnectionFactory().getConnection().scan(options);
                boolean found = false;
                while (cursor.hasNext()) {
                    found = true;
                    String key = new String(cursor.next());
                    System.out.println("  Key: " + key);
                    Long size = template.opsForHash().size(key);
                    System.out.println("    -> 已分配工作站数量: " + size);
                }
                if (!found) {
                    System.out.println("  未找到任何 snowflake 相关Key");
                }
            } catch (Exception e) {
                System.out.println("  扫描失败: " + e.getMessage());
            }

            System.out.println("\n========================================\n");
        }
    }
}