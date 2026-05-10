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
 * </p>
 *
 * <p>
 * 时间窗口说明：
 * <ul>
 *   <li>每8毫秒为一个时间窗口，每个窗口可生成0~4095个ID</li>
 *   <li>单实例理论产能：4096 / 0.008 = 51.2万ID/秒</li>
 *   <li>结合1024个工作站ID，横向扩展后产能可达5.2亿ID/秒</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2024-11-06
 * @version 1.0
 */
public class SnowflakeIdGenerator implements IdGenerator {

    /**
     * 时间窗口长度（每多少毫秒产生最多4096个ID）。
     *
     * <p>
     * 选择8毫秒的原因：
     * <ul>
     *   <li>单实例理论产能：4096/0.008 = 51.2万ID/秒，满足超高并发场景</li>
     *   <li>结合1024个工作站ID，横向扩展后产能可达5.2亿ID/秒</li>
     *   <li>时间戳字段仍可支撑约138年（相对于DD_EPOCH）</li>
     * </ul>
     * </p>
     *
     * <p>
     * 重要提示：此值一旦投入生产环境则不能改变，否则可能产生ID冲突。
     * </p>
     */
    private static final long EVERY_FEW_MILLISECOND = 8;

    /**
     * 纪元时间戳（定鼎：2024-08-27 12:00:00，毛毛5岁），ID生成的时间起点。
     *
     * <p>
     * 此值一旦投入生产则不能改变，否则可能产生ID冲突。
     * </p>
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
     *
     * <p>
     * 计算公式：SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH + DATACENTER_ID_BITS_LENGTH
     * </p>
     */
    private long timestampLeftShiftLength;

    /**
     * 数据中心ID向左移的位数。
     *
     * <p>
     * 计算公式：SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH
     * </p>
     */
    private long datacenterLeftShiftLength;

    /**
     * 序列号（0~4095），同一时间窗口内的递增序号。
     */
    private long sequence = 0L;

    /**
     * 上次生成ID时的时间戳（以8ms为单位的整数值）。
     *
     * <p>
     * 用于判断是否发生时间回拨，以及控制同一时间窗口内的序列号递增。
     * </p>
     */
    private long lastTimestamp = -1L;

    /**
     * 时间回拨警告日志计数器（每N次输出一次警告，避免日志刷屏）。
     */
    private long timeBackNewIdLoopCount = 0;

    /**
     * 配置属性。
     *
     * <p>
     * 用于获取时间回拨最大允许值和警告日志输出频率等配置。
     * </p>
     */
    private final SnowflakeProperties properties;

    /**
     * 数据中心ID（0~7）。
     */
    private final long dataCenterId;

    /**
     * 工作站ID（0~1023）。
     */
    private final long workerId;

    /**
     * 雪花装配器。
     *
     * <p>
     * 负责加载上次运行状态、保存运行记录、释放工作站ID等操作。
     * </p>
     */
    private final SnowflakeAssembler snowflakeAssembler;

    /**
     * 构造函数。
     *
     * @param dataCenterId 数据中心ID（0~7）
     * @param workerId 工作站ID（0~1023）
     * @param snowflakeAssembler 雪花装配器
     * @param properties 配置属性
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
     *
     * <p>
     * 在Spring容器中注册后调用，用于校验ID参数、初始化位移计算常量、
     * 打印注册信息、加载上次运行状态等。
     * </p>
     *
     * @throws IllegalArgumentException 如果工作站ID或数据中心ID超出有效范围
     */
    public void init() {
        long maxWorkstationId = ~(-1L << WORKSTATION_ID_BITS_LENGTH);
        long maxDatacenterId = ~(-1L << DATACENTER_ID_BITS_LENGTH);
        if (this.workerId > maxWorkstationId || this.workerId < 0) {
            throw new IllegalArgumentException(String.format("工作站ID不能大于%d或小于0，当前预设值为%d", maxWorkstationId, this.workerId));
        }
        if (this.dataCenterId > maxDatacenterId || this.dataCenterId < 0) {
            throw new IllegalArgumentException(String.format("数据中心ID不能大于%d或小于0，当前值为%d", maxDatacenterId, this.dataCenterId));
        }
        this.sequenceMask = ~(-1L << SEQUENCE_BITS_LENGTH);
        this.timestampLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH + DATACENTER_ID_BITS_LENGTH;
        this.datacenterLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH;

        System.out.println("[Snowflake] 已在Spring容器中成功注册数据中心ID为" + this.dataCenterId + "和工作站ID为" + this.workerId + "的雪花算法ID生成器...");
        this.loadLastRunHistory();
    }

    /**
     * 加载上次运行状态（lastTimestamp和sequence），用于防止时间回拨后ID重复。
     *
     * <p>
     * Local模式从文件读取，Redis模式从Redis维护。
     * 运行记录格式：lastTimestamp|sequence|id|datetime|binary
     * </p>
     *
     * <p>
     * 如果加载失败（文件不存在或Redis中无记录），则使用默认值从头开始生成ID。
     * </p>
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
                        throw new IllegalStateException(String.format("雪花算法ID生成器最后运行记录%s里的Sequence参数已超出最大值%d，请检查...", line, this.sequenceMask));
                    }
                    this.lastTimestamp = lt;
                    this.sequence = seq;
                    System.out.println("[Snowflake] 雪花算法ID生成器已成功加载最后运行参数值" + line);
                    return;
                }
                throw new IllegalStateException(String.format("雪花算法ID生成器最后运行记录%s里的参数内容错误，请检查...", line));
            }
        }
    }

    /**
     * 容器销毁时保存运行状态并释放WorkerId。
     *
     * <p>
     * 此方法确保在应用关闭时：
     * <ol>
     *   <li>先保存当前的lastTimestamp和sequence到存储介质（文件或Redis）</li>
     *   <li>然后释放工作站ID资源，允许其他实例复用</li>
     * </ol>
     * </p>
     *
     * <p>
     * 保存格式：lastTimestamp|sequence|id|datetime|binary
     * </p>
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
     * <p>
     * ID生成算法：
     * <ol>
     *   <li>获取当前时间戳（8ms单位）</li>
     *   <li>检查时间回拨：
     *     <ul>
     *       <li>如果当前时间 < 上次时间，说明发生时钟回拨</li>
     *       <li>如果回拨差值超过阈值，抛出异常</li>
     *       <li>如果回拨差值在允许范围内，继续使用上次时间戳生成ID</li>
     *     </ul>
     *   </li>
     *   <li>序列号处理：
     *     <ul>
     *       <li>同一时间窗口内，序列号递增</li>
     *       <li>序列号达到4095时，等待下一时间窗口</li>
     *     </ul>
     *   </li>
     *   <li>位运算组装64位ID并返回</li>
     * </ol>
     * </p>
     *
     * @return 唯一的64位Long类型ID
     * @throws RuntimeException 如果系统时钟错误或发生严重时间回拨
     */
    @Override
    public synchronized long nextId() {
        long timestamp = currentTimeEveryFewMillis();
        long diffTimeBack = 0L;
        String msg;
        if (timestamp < DD_EPOCH) {
            msg = String.format("雪花算法ID生成器碰到错误的系统时间，错误的系统时间为：%s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            throw new RuntimeException(msg);
        }
        if (timestamp < lastTimestamp) {
            diffTimeBack = lastTimestamp - timestamp;

            if (diffTimeBack > (this.properties.getMaxTimeBackMills() / EVERY_FEW_MILLISECOND)) {
                LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.lastTimestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
                LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
                msg = String.format("雪花算法ID生成器碰到意外错误，这是因为系统时钟发生了回调，回调时间相差 %d 毫秒。上次时间 %s,当前时间 %s", diffTimeBack * EVERY_FEW_MILLISECOND, lastTime.toString(), nowTime.toString());
                throw new RuntimeException(msg);
            } else {
                if (timeBackNewIdLoopCount == 0) {
                    LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.lastTimestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
                    LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
                    System.out.println("[Snowflake] 雪花算法ID生成器碰到时间回调，时间相差 " + (diffTimeBack * EVERY_FEW_MILLISECOND) + " 毫秒。上次时间 " + lastTime + ",当前时间 " + nowTime);
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
     *
     * <p>
     * ID结构：[时间戳(41bit) | 数据中心ID(3bit) | 工作站ID(10bit) | 序列号(12bit)]
     * </p>
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
     * <p>
     * 当序列号耗尽时触发，此时业务线程会被阻塞，直到下一时间窗口到来。
     * 这种设计确保了即使在极端情况下也不会产生重复ID。
     * </p>
     *
     * @param lastTimestamp 上次生成ID的时间戳
     * @param diffTimeBack 时间回拨补偿值
     * @return 下一时间窗口的时间戳
     */
    protected long untilNextMillis(long lastTimestamp, long diffTimeBack) {
        System.out.println("[Snowflake] 雪花算法ID生成器碰到热点Key，" + EVERY_FEW_MILLISECOND + "毫秒内ID序列号耗尽，正等待下一" + EVERY_FEW_MILLISECOND + "毫秒到来...");
        long timestamp = currentTimeEveryFewMillis() + diffTimeBack;
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeEveryFewMillis() + diffTimeBack;
        }
        return timestamp;
    }

    /**
     * 返回当前系统时间（以8ms为单位的整数值）。
     *
     * <p>
     * 该方法控制了每8ms产生最多4096个ID的时间窗口。
     * </p>
     *
     * @return 当前时间（8ms单位）
     */
    protected long currentTimeEveryFewMillis() {
        return System.currentTimeMillis() / EVERY_FEW_MILLISECOND;
    }

    /**
     * 获取ID生成器所配置的工作站号。
     *
     * @return 工作站ID（0~1023）
     */
    @Override
    public long getWorkstationId() {
        return this.workerId;
    }

    /**
     * 获取ID生成器所配置的数据中心号。
     *
     * @return 数据中心ID（0~7）
     */
    @Override
    public long getDataCenterId() {
        return this.dataCenterId;
    }
}