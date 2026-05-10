package top.baoding.snowflake.core;

import lombok.Getter;

/**
 * 雪花ID管理器
 * <p>
 * 注册JVM级别的退出钩子，确保应用退出时执行资源释放。
 * </p>
 *
 * @author caror
 * @date 2025-09-16
 * @version 1.0
 */
public class SnowflakeManager {

    @Getter
    private static SnowflakeAssembler assembler;

    static {
        // 注册JVM退出钩子，确保应用退出时正确释放资源
        Runtime.getRuntime().addShutdownHook(new Thread(SnowflakeManager::doRelease, "Snowflake-Cleanup-Hook"));
    }

    /**
     * 注册实例。
     *
     * @param assembler 雪花装配器实例
     */
    public static void register(SnowflakeAssembler assembler) {
        SnowflakeManager.assembler = assembler;
    }

    /**
     * 执行真正的释放逻辑。
     */
    public static void doRelease() {
        if (assembler != null && !assembler.isReleased()) {
            try {
                if (assembler.getIdGenerator() != null) {
                    assembler.getIdGenerator().saveAndRelease();
                } else {
                    assembler.releaseWorkerId();
                }
            } catch (Exception e) {
                System.err.println("[Snowflake] >>> Resource release failed! Error: " + e.getMessage());
            }
        }
    }
}