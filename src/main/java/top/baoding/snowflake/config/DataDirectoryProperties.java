package top.baoding.snowflake.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据目录配置属性
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "baoding.snowflake.data-dir")
public class DataDirectoryProperties {

    /**
     * 数据文件存储目录路径
     */
    private String path = "/tmp/snowflake-data";
}