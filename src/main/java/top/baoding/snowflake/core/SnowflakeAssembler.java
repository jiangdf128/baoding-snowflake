package top.baoding.snowflake.core;

/**
 * 雪花ID算法装配器接口
 *
 * <p>
 * 定义雪花算法在不同运行模式下的公共操作。
 * 实现类负责管理工作站ID的分配、续期、释放以及运行状态的保存。
 * </p>
 *
 * <p>
 * 实现类说明：
 * <ul>
 *   <li>LocalFileSnowflakeAssembler: 本地文件模式，使用本地文件存储状态</li>
 *   <li>RedisSnowflakeAssembler: Redis模式，使用Redis存储状态和分配工作站ID</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2025-03-30
 * @version 1.0
 */
public interface SnowflakeAssembler {

    /**
     * 释放工作站ID资源。
     *
     * <p>
     * 当应用关闭时调用，释放之前分配的工作站ID。
     * 释放后该工作站ID可被其他实例申请使用。
     * </p>
     *
     * <p>
     * 实现要求：
     * <ul>
     *   <li>使用原子操作确保只释放一次</li>
     *   <li>验证实例标识匹配后再释放</li>
     *   <li>提供兜底方案处理异常情况</li>
     * </ul>
     * </p>
     */
    void releaseWorkerId();

    /**
     * 获取工作站ID对应的上次运行记录。
     *
     * <p>
     * 运行记录用于在应用重启时恢复状态，避免时间回拨导致ID冲突。
     * </p>
     *
     * <p>
     * 记录格式：lastTimestamp|sequence|id|datetime|binary
     * </p>
     *
     * <p>
     * 字段说明：
     * <ul>
     *   <li>lastTimestamp: 上次生成ID时的时间戳（8ms单位）</li>
     *   <li>sequence: 上次的序列号</li>
     *   <li>id: 上次生成的ID</li>
     *   <li>datetime: 格式化的时间</li>
     *   <li>binary: ID的二进制表示</li>
     * </ul>
     * </p>
     *
     * @return 上次运行记录，如果无记录则返回空字符串
     */
    String getLastRunHistory();

    /**
     * 保存运行的最后运行记录。
     *
     * <p>
     * 当应用关闭时调用，保存当前的lastTimestamp和sequence。
     * 保存的数据用于下次启动时恢复状态。
     * </p>
     *
     * <p>
     * 保存格式：lastTimestamp|sequence|id|datetime|binary
     * </p>
     *
     * <p>
     * 实现要求：
     * <ul>
     *   <li>使用原子操作确保只保存一次</li>
     *   <li>提供兜底方案处理异常情况</li>
     * </ul>
     * </p>
     *
     * @param runHistory 最后运行记录值
     */
    void saveRunHistory(String runHistory);

    /**
     * 检查资源是否已释放。
     *
     * <p>
     * 用于判断工作站ID是否已被释放，避免重复释放。
     * </p>
     *
     * @return true表示已释放，false表示未释放
     */
    boolean isReleased();

    /**
     * 获取管理的ID生成器。
     *
     * <p>
     * 如果IdGenerator尚未创建，返回null。
     * </p>
     *
     * @return ID生成器，如果未创建则返回null
     */
    IdGenerator getIdGenerator();

    /**
     * 设置管理的ID生成器。
     *
     * <p>
     * 在IdGenerator创建时调用此方法建立关联。
     * </p>
     *
     * @param idGenerator ID生成器
     */
    void setIdGenerator(IdGenerator idGenerator);
}