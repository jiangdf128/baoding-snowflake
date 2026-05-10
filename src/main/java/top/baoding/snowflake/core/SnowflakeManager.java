package top.baoding.snowflake.core;

import lombok.Getter;

/**
 * 雪花ID管理器
 *
 * <p>
 * 负责管理雪花算法实例的注册和JVM退出时的资源释放。
 * 通过JVM shutdown hook确保应用退出时正确保存状态并释放工作站ID。
 * </p>
 *
 * <p>
 * 使用场景：
 * <ul>
 *   <li>Spring容器正常关闭：通过ContextClosedEvent触发释放</li>
 *   <li>非正常退出（如kill -9）：通过JVM shutdown hook触发释放</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2025-09-16
 * @version 1.0
 */
public class SnowflakeManager {

    /**
     * 雪花装配器实例。
     *
     * <p>
     * 使用Getter注解暴露，供外部访问。
     * </p>
     */
    @Getter
    private static SnowflakeAssembler assembler;

    /**
     * 静态初始化块。
     *
     * <p>
     * 注册JVM级别的退出钩子，确保在Java进程退出前执行资源释放。
     * 线程名称为"Snowflake-Cleanup-Hook"，便于日志识别。
     * </p>
     *
     * <p>
     * 说明：
     * <ul>
     *   <li>此钩子在所有Spring容器关闭之后执行</li>
     *   <li>如果Spring的ContextClosedEvent已正确处理，此处不会重复释放</li>
     *   <li>主要作为兜底方案，防止非正常退出时资源未释放</li>
     * </ul>
     * </p>
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SnowflakeManager::doRelease, "Snowflake-Cleanup-Hook"));
    }

    /**
     * 注册雪花装配器实例。
     *
     * <p>
     * 应在雪花装配器构造时调用此方法注册。
     * 注册后的实例会在JVM退出时被调用释放资源。
     * </p>
     *
     * @param assembler 雪花装配器实例
     */
    public static void register(SnowflakeAssembler assembler) {
        SnowflakeManager.assembler = assembler;
    }

    /**
     * 执行真正的资源释放逻辑。
     *
     * <p>
     * 释放流程：
     * <ol>
     *   <li>检查装配器是否已注册</li>
     *   <li>检查资源是否已释放（防止重复释放）</li>
     *   <li>如果有IdGenerator实例，先保存状态再释放</li>
     *   <li>如果没有IdGenerator实例，直接释放工作站ID</li>
     * </ol>
     * </p>
     *
     * <p>
     * 异常处理：
     * <ul>
     *   <li>捕获所有异常避免JVM退出失败</li>
     *   <li>打印错误信息到标准错误流，便于排查</li>
     * </ul>
     * </p>
     *
     * <p>
     * 说明：
     * <ul>
     *   <li>此时可能Redis连接池已经被Spring关闭</li>
     *   <li>如果标准释放失败，RedisSnowflakeAssembler会尝试兜底方案</li>
     *   <li>兜底方案使用独立的Lettuce客户端连接</li>
     * </ul>
     * </p>
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
                System.err.println("[Snowflake] 资源释放失败，错误原因：" + e.getMessage());
            }
        }
    }
}