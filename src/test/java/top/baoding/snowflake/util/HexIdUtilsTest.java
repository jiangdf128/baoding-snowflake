package top.baoding.snowflake.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HexIdUtils工具类测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class HexIdUtilsTest {

    @Test
    void testToHex() {
        long id = 76957782447792129L;
        String hex = HexIdUtils.toHex(id);
        assertNotNull(hex);
        assertEquals(15, hex.length());
    }

    @Test
    void testToBinary() {
        long id = 1L;
        String binary = HexIdUtils.toBinary(id);
        assertEquals("1", binary);
    }

    @Test
    void testFromHex() {
        long originalId = 76957782447792129L;
        String hex = HexIdUtils.toHex(originalId);
        long restored = HexIdUtils.fromHex(hex);
        assertEquals(originalId, restored);
    }

    @Test
    void testRoundTrip() {
        long originalId = System.currentTimeMillis();
        String hex = HexIdUtils.toHex(originalId);
        long restoredId = HexIdUtils.fromHex(hex);
        assertEquals(originalId, restoredId);
    }

    @Test
    void testToReadableString() {
        long id = 76957782447792129L;
        String readable = HexIdUtils.toReadableString(id, 0, 0);
        assertNotNull(readable);
        assertTrue(readable.contains("ID:"));
    }

    @Test
    void testHexConversionBidirectional() {
        long[] testIds = {1L, 100L, 9999999999999L, Long.MAX_VALUE / 2};
        for (long id : testIds) {
            String hex = HexIdUtils.toHex(id);
            long restored = HexIdUtils.fromHex(hex);
            assertEquals(id, restored, "十六进制转换应该是可逆的");
        }
    }

    @Test
    void testBinaryConversion() {
        long id = 1024L;
        String binary = HexIdUtils.toBinary(id);
        assertTrue(binary.startsWith("1"), "1024的二进制应该以1开头");
    }

    @Test
    void testToReadableStringContainsAllParts() {
        String result = HexIdUtils.toReadableString(1000000000L, 1, 2);
        assertTrue(result.contains("1"), "应该包含数据中心ID");
        assertTrue(result.contains("2"), "应该包含工作站ID");
        assertTrue(result.contains("1000000000"), "应该包含ID值");
    }
}