package top.baoding.snowflake.assembler;

import top.baoding.snowflake.core.IdGenerator;
import top.baoding.snowflake.core.SnowflakeAssembler;
import top.baoding.snowflake.core.SnowflakeManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.*;

/**
 * 本地文件模式雪花ID装配器
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
@Slf4j
public class LocalFileSnowflakeAssembler implements SnowflakeAssembler {

    private static final String ID_LASTTIME_FILENAME_FORMAT = "id-%d-%d-%s.lasttime";

    private final long dataCenterId;
    private final long workerId;
    private final String port;
    private final String dataPath;

    @Getter
    private IdGenerator idGenerator;

    public LocalFileSnowflakeAssembler(long dataCenterId, long workerId, String port, String dataPath) {
        this.dataCenterId = dataCenterId;
        this.workerId = workerId;
        this.port = port;
        this.dataPath = dataPath;
        SnowflakeManager.register(this);
    }

    protected String getWorkDirectoryPath() {
        if (StringUtils.hasText(this.dataPath)) {
            File directory = new File(this.dataPath);
            if (directory.exists() || directory.mkdirs()) {
                return directory.getAbsolutePath();
            } else {
                throw new RuntimeException(String.format("配置的工作路径 %s 创建失败！", this.dataPath));
            }
        }
        return new File(System.getProperty("user.dir")).getAbsolutePath();
    }

    private String getIdLastTimeFileName() {
        return String.format(ID_LASTTIME_FILENAME_FORMAT, this.dataCenterId, this.workerId, this.port);
    }

    @Override
    public void releaseWorkerId() {
    }

    @Override
    public String getLastRunHistory() {
        File file = new File(this.getWorkDirectoryPath(), this.getIdLastTimeFileName());
        if (file.exists()) {
            log.info("从文件 {} 中加载雪花算法ID生成器的上次运行参数...", file.getAbsolutePath());
            try (BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath(), java.nio.charset.StandardCharsets.UTF_8))) {
                return reader.readLine();
            } catch (IOException e) {
                throw new RuntimeException("加载雪花算法ID生成器的上次运行参数碰到意外错误！", e);
            }
        }
        return "";
    }

    @Override
    public void saveRunHistory(String runHistory) {
        File file = new File(this.getWorkDirectoryPath(), this.getIdLastTimeFileName());
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsolutePath(), java.nio.charset.StandardCharsets.UTF_8))) {
            bw.write(runHistory);
            log.info("已成功保存雪花算法ID生成器的上次运行参数 {}", runHistory);
        } catch (IOException e) {
            log.error("保存文件 {} 碰到意外错误", this.getIdLastTimeFileName(), e);
            throw new RuntimeException("保存雪花算法ID生成器的上次运行参数到文件碰到意外错误！", e);
        }
    }

    @Override
    public boolean isReleased() {
        return true;
    }

    @Override
    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }
}