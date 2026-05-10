package top.baoding.snowflake.core;

/**
 * 雪花ID算法装配器接口
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
public interface SnowflakeAssembler {

    /**
     * 释放工作站ID资源。
     */
    void releaseWorkerId();

    /**
     * 获取工作站ID对应的上次运行最后记录。
     *
     * @return 上次运行记录，格式如：1743079365142|1|76957782447792129|2025-03-27T20:42:45.142|100010001011010001011001000000101100000001010000000000001
     */
    String getLastRunHistory();

    /**
     * 保存运行的最后运行记录。
     *
     * @param runHistory 最后运行记录值
     */
    void saveRunHistory(String runHistory);

    /**
     * 是否已经释放资源。
     *
     * @return 是否已经释放资源
     */
    boolean isReleased();

    /**
     * 获取管理的ID生成器。
     *
     * @return ID生成器
     */
    IdGenerator getIdGenerator();

    /**
     * 设置管理的ID生成器。
     *
     * @param idGenerator ID生成器
     */
    void setIdGenerator(IdGenerator idGenerator);
}