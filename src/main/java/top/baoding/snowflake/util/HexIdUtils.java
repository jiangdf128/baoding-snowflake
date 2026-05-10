package top.baoding.snowflake.util;

/**
 * 雪花ID工具类
 * <p>
 * 提供ID的十六进制和二进制转换功能。
 * </p>
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
public class HexIdUtils {

    private HexIdUtils() {
    }

    /**
     * 将64位雪花ID转换为十六进制字符串。
     *
     * @param id 64位雪花ID
     * @return 十六进制字符串（不带0x前缀）
     */
    public static String toHex(long id) {
        return Long.toHexString(id);
    }

    /**
     * 将64位雪花ID转换为二进制字符串。
     *
     * @param id 64位雪花ID
     * @return 二进制字符串
     */
    public static String toBinary(long id) {
        return Long.toBinaryString(id);
    }

    /**
     * 解析十六进制字符串为64位ID。
     *
     * @param hex 十六进制字符串
     * @return 64位雪花ID
     */
    public static long fromHex(String hex) {
        return Long.parseLong(hex, 16);
    }

    /**
     * 格式化ID为带分隔符的可读字符串。
     * <p>
     * 格式：timestamp|dataCenterId|workstationId|sequence
     * </p>
     *
     * @param id 64位雪花ID
     * @param datacenterId 数据中心ID
     * @param workstationId 工作站ID
     * @return 可读字符串
     */
    public static String toReadableString(long id, long datacenterId, long workstationId) {
        long sequence = id & 0xFFF;
        long timestamp = (id >> 22) + 21562044; // 还原为相对于DD_EPOCH的时间戳
        return String.format("ID: %d | DC: %d | Worker: %d | Seq: %d", id, datacenterId, workstationId, sequence);
    }
}