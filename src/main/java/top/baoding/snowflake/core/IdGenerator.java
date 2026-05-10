package top.baoding.snowflake.core;

/**
 * 分布式ID生成器接口
 *
 * <p>
 * 定义雪花算法ID生成器的基本操作，所有实现类都必须实现此接口。
 * </p>
 *
 * <p>
 * ID生成器职责：
 * <ul>
 *   <li>生成全局唯一的64位Long类型ID</li>
 *   <li>提供工作站ID和数据中心ID的访问</li>
 *   <li>管理运行状态的保存和资源释放</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2025-09-19
 * @version 1.0
 */
public interface IdGenerator {

    /**
     * 获取下一个全局唯一的 Long 类型 ID。
     *
     * <p>
     * 此方法是ID生成器的核心方法，调用时必须保证线程安全。
     * 生成的ID遵循雪花算法结构：时间戳(41bit) + 数据中心ID(3bit) + 工作站ID(10bit) + 序列号(12bit)
     * </p>
     *
     * <p>
     * 重要说明：
     * <ul>
     *   <li>返回的ID是64位Long类型</li>
     *   <li>理论上保证全局唯一性</li>
     *   <li>如果系统时钟异常（如严重回拨），可能抛出异常</li>
     * </ul>
     * </p>
     *
     * @return 下一个全局唯一的ID
     * @throws RuntimeException 如果系统时钟异常
     */
    long nextId();

    /**
     * 获取 ID 生成器所配置的工作站号（Worker ID）。
     *
     * <p>
     * 工作站号用于在分布式环境中标识不同的工作站/实例。
     * </p>
     *
     * <p>
     * 取值范围：0 ~ 1023（10bit）
     * </p>
     *
     * @return 工作站号
     */
    long getWorkstationId();

    /**
     * 获取 ID 生成器所配置的数据中心号（Data Center ID）。
     *
     * <p>
     * 数据中心号用于标识不同的数据中心或服务集群。
     * </p>
     *
     * <p>
     * 取值范围：0 ~ 7（3bit）
     * </p>
     *
     * @return 数据中心号
     */
    long getDataCenterId();

    /**
     * 本类实例销毁的时候，对上次取了ID后的lastTimestamp和sequence进行保存。
     *
     * <p>
     * 此方法在应用关闭或ID生成器销毁时被调用，确保：
     * <ol>
     *   <li>保存当前的运行状态（lastTimestamp和sequence）</li>
     *   <li>释放工作站ID资源，允许其他实例复用</li>
     * </ol>
     * </p>
     *
     * <p>
     * 保存的数据用于下次启动时恢复状态，避免时间回拨导致ID冲突。
     * </p>
     *
     * <p>
     * 实现说明：
     * <ul>
     *   <li>Local模式：将状态持久化到文件</li>
     *   <li>Redis模式：将状态保存到Redis并释放工作站ID</li>
     * </ul>
     * </p>
     */
    void saveAndRelease();
}