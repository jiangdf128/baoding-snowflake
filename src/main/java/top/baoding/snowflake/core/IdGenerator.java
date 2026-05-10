package top.baoding.snowflake.core;

/**
 * 分布式ID生成器接口
 *
 * @author caror
 * @date 2025-09-19
 * @version 1.0
 */
public interface IdGenerator {

    /**
     * 获取下一个全局唯一的 Long 类型 ID。
     *
     * @return 下一个 ID
     */
    long nextId();

    /**
     * 获取 ID 生成器所配置的工作站号（Worker ID）。
     *
     * @return 工作站号
     */
    long getWorkstationId();

    /**
     * 获取 ID 生成器所配置的数据中心号（Data Center ID）。
     *
     * @return 数据中心号
     */
    long getDataCenterId();

    /**
     * 本类实例销毁的时候，对上次取了ID后的lastTimestamp和sequence进行保存。
     */
    void saveAndRelease();
}