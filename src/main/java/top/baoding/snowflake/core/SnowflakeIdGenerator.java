package top.baoding.snowflake.core;

import top.baoding.snowflake.config.SnowflakeProperties;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 雪花ID生成器实现
 *
 * <p>
 * ID结构（64位）：时间戳(41bit) + 数据中心ID(3bit) + 工作站ID(10bit) + 序列号(12bit)
 * <ul>
 *   <li>时间戳：相对于 DD_EPOCH 的偏移量，约可支撑138年</li>
 *   <li>数据中心ID：0~7 共8个</li>
 *   <li>工作站ID：0~1023 共1024个（Redis模式下自动分配/释放）</li>
 *   <li>序列号：每8ms窗口内0~4095</li>
 * </ul>
 *
 * <p>
 * 产能估算：
 * <ul>
 *   <li>单实例：4096 / 0.008 = 51.2万ID/秒</li>
 *   <li>横向扩展（1024实例）：5.2亿ID/秒</li>
 * </ul>
 *
 * @author caror
 * @date 2024-11-06
 * @version 1.0
 */
public class SnowflakeIdGenerator implements IdGenerator {

    /**
     * 时间窗口长度（每多少毫秒产生最多4096个ID）。
     * 选择8毫秒的原因：
     * 1. 单实例理论产能：4096/0.008 = 51.2万ID/秒，满足超高并发场景
     * 2. 结合1024个工作站ID，横向扩展后产能可达5.2亿ID/秒
     * 3. 时间戳字段仍可支撑约138年（相对于DD_EPOCH）
     * 重要提示：此值一旦投入生产环境则不能改变，否则可能产生ID冲突。
     */
    private static final long EVERY_FEW_MILLISECOND = 8;

    /**
     * 纪元时间戳（定鼎：2024-08-27 12:00:00），ID生成的时间起点。
     * 此值一旦投入生产则不能改变，否则可能产生ID冲突。
     */
    private static final long DD_EPOCH = 1724760000000L / EVERY_FEW_MILLISECOND;

    /**
     * 工作站ID所占Bit位数（10位，范围0~1023）。
     */
    public static final long WORKSTATION_ID_BITS_LENGTH = 10L;

    /**
     * 数据中心ID所占Bit位数（3位，范围0~7）。
     */
    public static final long DATACENTER_ID_BITS_LENGTH = 3L;

    /**
     * 序列号所占Bit位数（12位，范围0~4095）。
     */
    private static final long SEQUENCE_BITS_LENGTH = 12L;

    /**
     * 生成序列的掩码（4095），用于限制序列号在0~4095范围内。
     */
    private long sequenceMask;

    /**
     * 时间戳向左移的位数。
     */
    private long timestampLeftShiftLength;

    /**
     * 数据中心ID向左移的位数。
     */
    private long datacenterLeftShiftLength;

    /**
     * 序列号（0~4095），同一时间窗口内的递增序号。
     */
    private long sequence = 0L;

    /**
     * 上次生成ID时的时间戳（以8ms为单位的整数值）。
     */
    private long lastTimestamp = -1L;

    /**
     * 时间回拨警告日志计数器（每N次输出一次警告，避免日志刷屏）。
     */
    private long timeBackNewIdLoopCount = 0;

    /**
     * 配置属性。
     */
    private final SnowflakeProperties properties;

    /**
     * 数据中心ID。
     */
    private final long dataCenterId;

    /**
     * 工作站ID。
     */
    private final long workerId;

    /**
     * 雪花装配器。
     */
    private final SnowflakeAssembler snowflakeAssembler;

    /**
     * 构造函数。
     *
     * @param dataCenterId        数据中心ID
     * @param workerId            工作站ID
     * @param snowflakeAssembler   雪花装配器
     * @param properties          配置属性
     */
    public SnowflakeIdGenerator(long dataCenterId, long workerId, SnowflakeAssembler snowflakeAssembler, SnowflakeProperties properties) {
        this.dataCenterId = dataCenterId;
        this.workerId = workerId;
        this.snowflakeAssembler = snowflakeAssembler;
        this.properties = properties;
        snowflakeAssembler.setIdGenerator(this);
    }

    /**
     * 初始化方法。
     */
    public void init() {
        long maxWorkstationId = ~(-1L << WORKSTATION_ID_BITS_LENGTH);
        long maxDatacenterId = ~(-1L << DATACENTER_ID_BITS_LENGTH);
        if (this.workerId > maxWorkstationId || this.workerId < 0) {
            throw new IllegalArgumentException(String.format("工作站ID不能大于%d或小于0，当前值为%d", maxWorkstationId, this.workerId));
        }
        if (this.dataCenterId > maxDatacenterId || this.dataCenterId < 0) {
            throw new IllegalArgumentException(String.format("数据中心ID不能大于%d或小于0，当前值为%d", maxDatacenterId, this.dataCenterId));
        }
        this.sequenceMask = ~(-1L << SEQUENCE_BITS_LENGTH);
        this.timestampLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH + DATACENTER_ID_BITS_LENGTH;
        this.datacenterLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH;

        System.out.println("[Snowflake] Registered Snowflake ID Generator with datacenterId=" + this.dataCenterId + ", workerId=" + this.workerId);
        this.loadLastRunHistory();
    }

    /**
     * 加载上次运行状态（lastTimestamp和sequence），用于防止时间回拨后ID重复。
     * Local模式从文件读取，Redis模式由Redis维护。
     */
    protected void loadLastRunHistory() {
        String line = this.snowflakeAssembler.getLastRunHistory();
        if (line != null && !line.isEmpty()) {
            if (line.contains("|")) {
                String[] pars = line.split("\\|");
                if (pars.length > 2) {
                    long lt = Long.parseLong(pars[0]);
                    long seq = Long.parseLong(pars[1]);
                    if (seq > this.sequenceMask) {
                        throw new IllegalStateException(String.format("最后运行记录中的Sequence参数已超出最大值%d，请检查...", this.sequenceMask));
                    }
                    this.lastTimestamp = lt;
                    this.sequence = seq;
                    System.out.println("[Snowflake] Loaded last run parameters: " + line);
                    return;
                }
                throw new IllegalStateException(String.format("最后运行记录参数内容错误，请检查...", line));
            }
        }
    }

    /**
     * 容器销毁时保存运行状态并释放WorkerId。
     */
    @Override
    public void saveAndRelease() {
        synchronized (this) {
            try {
                if (this.lastTimestamp > 0) {
                    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.lastTimestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
                    long id = this.calcId(this.lastTimestamp);
                    String parsLine = String.format("%d|%d|%d|%s|%s", this.lastTimestamp, this.sequence, id, date, Long.toBinaryString(id));
                    this.snowflakeAssembler.saveRunHistory(parsLine);
                }
            } finally {
                this.snowflakeAssembler.releaseWorkerId();
            }
        }
    }

    /**
     * 获取下一个雪花ID（线程安全）。
     *
     * @return 唯一的64位Long类型ID
     */
    @Override
    public synchronized long nextId() {
        long timestamp = currentTimeEveryFewMillis();
        long diffTimeBack = 0L;
        String msg;
        if (timestamp < DD_EPOCH) {
            msg = String.format("系统时间错误，错误的系统时间为：%s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            throw new RuntimeException(msg);
        }
        if (timestamp < lastTimestamp) {
            diffTimeBack = lastTimestamp - timestamp;

            LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.lastTimestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
            LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());

            if (diffTimeBack > (this.properties.getMaxTimeBackMills() / EVERY_FEW_MILLISECOND)) {
                msg = String.format("时钟回拨超过阈值，回调时间相差 %d 毫秒。上次时间 %s,当前时间 %s", diffTimeBack * EVERY_FEW_MILLISECOND, lastTime.toString(), nowTime.toString());
                throw new RuntimeException(msg);
            } else {
                if (timeBackNewIdLoopCount == 0) {
                    System.out.println("[Snowflake] WARNING: Clock backflow detected, diff=" + (diffTimeBack * EVERY_FEW_MILLISECOND) + "ms. Last: " + lastTime + ", Current: " + nowTime);
                }
                timeBackNewIdLoopCount += 1;
                if (timeBackNewIdLoopCount >= this.properties.getTimeBackWarnLoopCount()) {
                    timeBackNewIdLoopCount = 0;
                }
                if (((sequence + 1) & sequenceMask) == 0) {
                    timestamp = lastTimestamp + 1;
                } else {
                    timestamp = lastTimestamp;
                }
            }
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = untilNextMillis(lastTimestamp, diffTimeBack);
            }
        } else {
            sequence = ThreadLocalRandom.current().nextInt(0, 999_999_999) % 2;
        }
        lastTimestamp = timestamp;
        return this.calcId(timestamp);
    }

    /**
     * 位运算组装64位ID。
     * ID结构：[时间戳 | 数据中心ID | 工作站ID | 序列号]
     *
     * @param calcTimestamp 时间戳（相对于DD_EPOCH的偏移量）
     * @return 64位雪花ID
     */
    protected long calcId(long calcTimestamp) {
        return ((calcTimestamp - DD_EPOCH) << timestampLeftShiftLength)
                | (this.getDataCenterId() << datacenterLeftShiftLength)
                | (this.getWorkstationId() << SEQUENCE_BITS_LENGTH)
                | sequence;
    }

    /**
     * 等待下一时间窗口（自旋直到获得新时间戳）。
     *
     * @param lastTimestamp 上次生成ID的时间戳
     * @param diffTimeBack  时间回拨补偿值
     * @return 下一时间窗口的时间戳
     */
    protected long untilNextMillis(long lastTimestamp, long diffTimeBack) {
        System.out.println("[Snowflake] Sequence exhausted, waiting for next time window...");
        long timestamp = currentTimeEveryFewMillis() + diffTimeBack;
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeEveryFewMillis() + diffTimeBack;
        }
        return timestamp;
    }

    /**
     * 返回当前系统时间（以8ms为单位的整数值）。
     *
     * @return 当前时间（8ms单位）
     */
    protected long currentTimeEveryFewMillis() {
        return System.currentTimeMillis() / EVERY_FEW_MILLISECOND;
    }

    /**
     * @return 获取工作站号
     */
    @Override
    public long getWorkstationId() {
        return this.workerId;
    }

    /**
     * @return 获取数据中心号
     */
    @Override
    public long getDataCenterId() {
        return this.dataCenterId;
    }
}