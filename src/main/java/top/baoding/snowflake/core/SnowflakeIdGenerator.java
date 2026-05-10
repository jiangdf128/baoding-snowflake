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
 * @author caror
 * @date 2024-11-06
 * @version 1.0
 */
public class SnowflakeIdGenerator implements IdGenerator {

    /**
     * 时间窗口长度。
     */
    private static final long EVERY_FEW_MILLISECOND = 8;

    /**
     * 纪元时间戳。
     */
    private static final long DD_EPOCH = 1724760000000L / EVERY_FEW_MILLISECOND;

    /**
     * 工作站ID所占Bit位数。
     */
    public static final long WORKSTATION_ID_BITS_LENGTH = 10L;

    /**
     * 数据中心ID所占Bit位数。
     */
    public static final long DATACENTER_ID_BITS_LENGTH = 3L;

    /**
     * 序列号所占Bit位数。
     */
    private static final long SEQUENCE_BITS_LENGTH = 12L;

    /**
     * 生成序列的掩码。
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
     * 序列号。
     */
    private long sequence = 0L;

    /**
     * 上次生成ID时的时间戳。
     */
    private long lastTimestamp = -1L;

    /**
     * 时间回拨警告日志计数器。
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
            throw new IllegalArgumentException(String.format("工作站ID不能大于%d或小于0，当前预设值为%d", maxWorkstationId, this.workerId));
        }
        if (this.dataCenterId > maxDatacenterId || this.dataCenterId < 0) {
            throw new IllegalArgumentException(String.format("数据中心ID不能大于%d或小于0，当前预设值为%d", maxDatacenterId, this.dataCenterId));
        }
        this.sequenceMask = ~(-1L << SEQUENCE_BITS_LENGTH);
        this.timestampLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH + DATACENTER_ID_BITS_LENGTH;
        this.datacenterLeftShiftLength = SEQUENCE_BITS_LENGTH + WORKSTATION_ID_BITS_LENGTH;

        System.out.println("[Snowflake] 已在Spring容器中成功注册数据中心ID为" + this.dataCenterId + "和工作站ID为" + this.workerId + "的雪花算法ID生成器...");
        this.loadLastRunHistory();
    }

    /**
     * 加载上次运行状态。
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

            LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.lastTimestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());
            LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp * EVERY_FEW_MILLISECOND), ZoneId.systemDefault());

            if (diffTimeBack > (this.properties.getMaxTimeBackMills() / EVERY_FEW_MILLISECOND)) {
                msg = String.format("雪花算法ID生成器碰到意外错误，这是因为系统时钟发生了回调，回调时间相差 %d 毫秒。上次时间 %s,当前时间 %s", diffTimeBack * EVERY_FEW_MILLISECOND, lastTime.toString(), nowTime.toString());
                throw new RuntimeException(msg);
            } else {
                if (timeBackNewIdLoopCount == 0) {
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
            if (((sequence + 1) & sequenceMask) == 0) {
                long lastMaxId = this.calcId(timestamp);
            }
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
     */
    protected long calcId(long calcTimestamp) {
        return ((calcTimestamp - DD_EPOCH) << timestampLeftShiftLength)
                | (this.getDataCenterId() << datacenterLeftShiftLength)
                | (this.getWorkstationId() << SEQUENCE_BITS_LENGTH)
                | sequence;
    }

    /**
     * 等待下一时间窗口。
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
     * 返回当前系统时间。
     */
    protected long currentTimeEveryFewMillis() {
        return System.currentTimeMillis() / EVERY_FEW_MILLISECOND;
    }

    @Override
    public long getWorkstationId() {
        return this.workerId;
    }

    @Override
    public long getDataCenterId() {
        return this.dataCenterId;
    }
}