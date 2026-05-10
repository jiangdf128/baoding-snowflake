package top.baoding.snowflake.core;

import top.baoding.snowflake.config.SnowflakeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 雪花ID生成器单元测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class SnowflakeIdGeneratorTest {

    private SnowflakeProperties properties;
    private MockSnowflakeAssembler assembler;
    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        properties = new SnowflakeProperties();
        properties.setDatacenterId(0);
        properties.setWorkstationId(0);
        properties.setMaxTimeBackMills(86400000);
        properties.setTimeBackWarnLoopCount(5000);

        assembler = new MockSnowflakeAssembler();
        generator = new SnowflakeIdGenerator(0, 0, assembler, properties);
        generator.init();
    }

    @Test
    void testNextId() {
        long id = generator.nextId();
        assertTrue(id > 0, "生成的ID应该大于0");
        System.out.println("Generated ID: " + id);
        System.out.println("Binary: " + Long.toBinaryString(id));
    }

    @Test
    void testUniqueIds() {
        int count = 10000;
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }
        assertEquals(count, ids.size(), "所有生成的ID应该唯一");
    }

    @Test
    void testIdStructure() {
        long id = generator.nextId();
        // 验证ID结构：最高位应该为0（正数）
        assertTrue(id > 0, "ID应该为正数");
        // 验证ID在合理范围内
        assertTrue(id < Long.MAX_VALUE, "ID应该在Long范围内");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];
        Set<Long> allIds = java.Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    allIds.add(generator.nextId());
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * idsPerThread, allIds.size(), "并发生成的ID应该全部唯一");
    }

    @Test
    void testGetWorkstationId() {
        assertEquals(0, generator.getWorkstationId());
    }

    @Test
    void testGetDataCenterId() {
        assertEquals(0, generator.getDataCenterId());
    }

    @Test
    void testInvalidDatacenterId() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(10); // 超出范围
        props.setWorkstationId(0);

        assertThrows(IllegalArgumentException.class, () -> {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(10, 0, assembler, props);
            gen.init();
        });
    }

    @Test
    void testInvalidWorkstationId() {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setDatacenterId(0);
        props.setWorkstationId(2000); // 超出1023范围

        assertThrows(IllegalArgumentException.class, () -> {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 2000, assembler, props);
            gen.init();
        });
    }

    /**
     * Mock装配器，用于测试
     */
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