package top.baoding.snowflake.core;

import top.baoding.snowflake.config.SnowflakeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        assertTrue(id > 0, "ID应该为正数");
        assertTrue(id < Long.MAX_VALUE, "ID应该在Long范围内");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Long> allIds = java.util.Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    allIds.add(generator.nextId());
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

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
        props.setDatacenterId(10);
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
        props.setWorkstationId(2000);

        assertThrows(IllegalArgumentException.class, () -> {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0, 2000, assembler, props);
            gen.init();
        });
    }

    @Test
    void testIdIncrementingInSameWindow() {
        long firstId = generator.nextId();
        long secondId = generator.nextId();
        assertTrue(secondId > firstId, "同一时间窗口内ID应该递增");
    }

    @Test
    void testDifferentWorkerIdsCanCoexist() {
        SnowflakeProperties props1 = new SnowflakeProperties();
        props1.setDatacenterId(0);
        props1.setWorkstationId(1);
        props1.setMaxTimeBackMills(86400000);
        props1.setTimeBackWarnLoopCount(5000);

        SnowflakeProperties props2 = new SnowflakeProperties();
        props2.setDatacenterId(0);
        props2.setWorkstationId(2);
        props2.setMaxTimeBackMills(86400000);
        props2.setTimeBackWarnLoopCount(5000);

        MockSnowflakeAssembler assembler1 = new MockSnowflakeAssembler();
        MockSnowflakeAssembler assembler2 = new MockSnowflakeAssembler();

        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(0, 1, assembler1, props1);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(0, 2, assembler2, props2);
        gen1.init();
        gen2.init();

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();

        assertNotEquals(id1, id2, "不同工作站ID生成的ID应该不同");
    }

    @Test
    void testHighThroughput() {
        long startTime = System.currentTimeMillis();
        int count = 100000;
        for (int i = 0; i < count; i++) {
            generator.nextId();
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("生成" + count + "个ID耗时：" + duration + "ms");
        assertTrue(duration < 10000, "生成10万个ID应该在10秒内完成");
    }

    @Test
    void testIdBinaryRepresentation() {
        long id = generator.nextId();
        String binary = Long.toBinaryString(id);
        assertTrue(binary.length() <= 63, "二进制字符串应该小于等于63位(不含符号位)");
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