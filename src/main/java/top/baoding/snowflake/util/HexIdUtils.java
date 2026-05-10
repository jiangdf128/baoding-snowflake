package top.baoding.snowflake.util;

/**
 * 雪花ID工具类
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
public class HexIdUtils {

    private HexIdUtils() {
    }

    public static String toHex(long id) {
        return Long.toHexString(id);
    }

    public static String toBinary(long id) {
        return Long.toBinaryString(id);
    }

    public static long fromHex(String hex) {
        return Long.parseLong(hex, 16);
    }

    public static String toReadableString(long id, long datacenterId, long workstationId) {
        long sequence = id & 0xFFF;
        long timestamp = (id >> 22) + 21562044;
        return String.format("ID: %d | DC: %d | Worker: %d | Seq: %d", id, datacenterId, workstationId, sequence);
    }

    public static String toReadableString(long id) {
        long datacenterId = (id >> 22) & 0x07;
        long workstationId = (id >> 12) & 0x3FF;
        long sequence = id & 0xFFF;
        return String.format("ID: %d | DC: %d | Worker: %d | Seq: %d", id, datacenterId, workstationId, sequence);
    }
}