package top.baoding.snowflake.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 雪花算法配置属性
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "baoding.snowflake")
public class SnowflakeProperties {

    /**
     * 运行模式：local 或 redis
     */
    private String mode = "redis";

    /**
     * ID应用范围，如果不设置默认使用微服务名
     */
    private String scope = "";

    /**
     * 工作站ID号（仅Local模式需要手动配置，Redis模式自动分配）
     */
    private long workstationId = 0;

    /**
     * 数据中心ID号（必须手动配置，范围0~7）
     */
    private long datacenterId = 0;

    /**
     * 允许时间回拨最大的毫秒数，超过此值会抛出运行时错误
     */
    private long maxTimeBackMills = 86400000;

    /**
     * 出现时间回拨后，每多少次新ID后产生一次警告日志
     */
    private long timeBackWarnLoopCount = 5000;
}