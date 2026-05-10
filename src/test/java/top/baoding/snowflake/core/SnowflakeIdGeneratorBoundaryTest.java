package top.baoding.snowflake.core;

import top.baoding.snowflake.config.SnowflakeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnowflakeIdGenerator边界条件测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class SnowflakeIdGeneratorBoundaryTest {

    private SnowflakeProperties properties;
    private MockSnowflakeAssembler assembler;

    @BeforeEach
    void setUp() {
        properties = new SnowflakeProperties();
        properties.setDatacenterId(0);
        properties.setWorkstationId(0);
        properties.setMaxTimeBackMills(86400000);
        properties.setTimeBackWarnLoopCount(5000);
        assembler = new MockSnowflakeAssembler();
    }

    @Test
    void testMaxDatacenterId() {
        long maxDatacenterId = ~(-1L << SnowflakeIdGenerator.DATACENTER_ID_BITS_LENGTH);
        assertEquals(7, maxDatacenterId);

        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(7);
        props.setWorkstationId(0);

        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(7, 0, assembler, props);
        gen.init();

        assertEquals(7, gen.getDataCenterId());
    }

    @Test
    void testMaxWorkstationId() {
        long maxWorkstationId = ~(-1L << SnowflakeIdGenerator.WORKSTATION_ID_BITS_LENGTH);
        assertEquals(1023, maxWorkstationId);

        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(0);
        props.setWorkstationId(1023);

        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 1023, assembler, props);
        gen.init();

        assertEquals(1023, gen.getWorkstationId());
    }

    @Test
    void testSequenceMask() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 0, assembler, properties);
        gen.init();

        long id1 = gen.nextId();
        long id2 = gen.nextId();
        long id3 = gen.nextId();

        assertTrue(id2 > id1);
        assertTrue(id3 > id2);
    }

    @Test
    void testZeroWorkstationId() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(0);
        props.setWorkstationId(0);

        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 0, assembler, props);
        gen.init();

        assertEquals(0, gen.getWorkstationId());
    }

    @Test
    void testZeroDatacenterId() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(0);
        props.setWorkstationId(0);

        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 0, assembler, props);
        gen.init();

        assertEquals(0, gen.getDataCenterId());
    }

    @Test
    void testMultipleInstancesWithDifferentDatacenter() {
        SnowflakeProperties props1 = new SnowflakeProperties();
        props1.setDatacenterId(0);
        props1.setWorkstationId(0);
        props1.setMaxTimeBackMills(86400000);
        props1.setTimeBackWarnLoopCount(5000);

        SnowflakeProperties props2 = new SnowflakeProperties();
        props2.setDatacenterId(1);
        props2.setWorkstationId(0);
        props2.setMaxTimeBackMills(86400000);
        props2.setTimeBackWarnLoopCount(5000);

        MockSnowflakeAssembler asm1 = new MockSnowflakeAssembler();
        MockSnowflakeAssembler asm2 = new MockSnowflakeAssembler();

        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(0, 0, asm1, props1);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(1, 0, asm2, props2);
        gen1.init();
        gen2.init();

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();

        assertNotEquals(id1, id2);
    }

    private static class MockSnowflakeAssembler implements SnowflakeAssembler {
        private IdGenerator idGenerator;

        @Override
        public void releaseWorkerId() {
        }

        @Override
        public String getLastRunHistory() {
            return "";
        }

        @Override
        public void saveRunHistory(String runHistory) {
        }

        @Override
        public boolean isReleased() {
            return true;
        }

        @Override
        public IdGenerator getIdGenerator() {
            return idGenerator;
        }

        @Override
        public void setIdGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
        }
    }
}