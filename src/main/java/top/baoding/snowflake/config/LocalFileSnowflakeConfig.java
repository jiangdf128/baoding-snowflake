package top.baoding.snowflake.config;

import top.baoding.snowflake.assembler.LocalFileSnowflakeAssembler;
import top.baoding.snowflake.core.IdGenerator;
import top.baoding.snowflake.core.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 本地文件雪花ID生成器配置
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@ConditionalOnProperty(name = "baoding.snowflake.mode", havingValue = "local")
public class LocalFileSnowflakeConfig {

    @Bean
    public IdGenerator localIdGenerator(
            SnowflakeProperties snowflakeProperties,
            DataDirectoryProperties dataDirectoryProperties,
            @Value("${server.port:8080}") String port) {

        var assembler = new LocalFileSnowflakeAssembler(
                snowflakeProperties.getDatacenterId(),
                snowflakeProperties.getWorkstationId(),
                port,
                dataDirectoryProperties.getPath()
        );

        var generator = new SnowflakeIdGenerator(
                snowflakeProperties.getDatacenterId(),
                snowflakeProperties.getWorkstationId(),
                assembler,
                snowflakeProperties
        );
        generator.init();
        return generator;
    }
}