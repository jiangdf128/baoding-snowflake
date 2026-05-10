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
        assertEquals("110dd8ddb5dd441", hex.toLowerCase());
    }

    @Test
    void testToBinary() {
        long id = 1L;
        String binary = HexIdUtils.toBinary(id);
        assertEquals("1", binary);
    }

    @Test
    void testFromHex() {
        String hex = "110dd8ddb5dd441";
        long id = HexIdUtils.fromHex(hex);
        assertEquals(76957782447792129L, id);
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
}