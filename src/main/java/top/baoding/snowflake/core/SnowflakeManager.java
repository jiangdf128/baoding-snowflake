package top.baoding.snowflake.core;

import lombok.Getter;

/**
 * 雪花ID管理器
 *
 * @author caror
 * @date 2025-09-16
 * @version 1.0
 */
public class SnowflakeManager {

    @Getter
    private static SnowflakeAssembler assembler;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SnowflakeManager::doRelease, "Snowflake-Cleanup-Hook"));
    }

    public static void register(SnowflakeAssembler assembler) {
        SnowflakeManager.assembler = assembler;
    }

    public static void doRelease() {
        if (assembler != null && !assembler.isReleased()) {
            try {
                if (assembler.getIdGenerator() != null) {
                    assembler.getIdGenerator().saveAndRelease();
                } else {
                    assembler.releaseWorkerId();
                }
            } catch (Exception e) {
                System.err.println("[Snowflake] 资源释放失败，错误原因：" + e.getMessage());
            }
        }
    }
}