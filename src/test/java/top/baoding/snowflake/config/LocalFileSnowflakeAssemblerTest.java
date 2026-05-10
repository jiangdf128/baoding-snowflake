package top.baoding.snowflake.config;

import top.baoding.snowflake.assembler.LocalFileSnowflakeAssembler;
import top.baoding.snowflake.core.SnowflakeIdGenerator;
import top.baoding.snowflake.core.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalFileSnowflakeAssembler集成测试
 *
 * @author caror
 * @date 2025-09-29
 * @version 1.0
 */
class LocalFileSnowflakeAssemblerTest {

    @TempDir
    Path tempDir;

    private SnowflakeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SnowflakeProperties();
        properties.setDatacenterId(0);
        properties.setWorkstationId(1);
        properties.setMaxTimeBackMills(86400000);
        properties.setTimeBackWarnLoopCount(5000);
    }

    @Test
    void testAssemberCreation() {
        LocalFileSnowflakeAssembler assembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        assertNotNull(assembler);
        assertTrue(assembler.isReleased());
    }

    @Test
    void testIdGeneration() {
        LocalFileSnowflakeAssembler assembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 1, assembler, properties);
        generator.init();

        long id = generator.nextId();
        assertTrue(id > 0);
        assertEquals(1, generator.getWorkstationId());
        assertEquals(0, generator.getDataCenterId());
    }

    @Test
    void testUniqueIdsAcrossMultipleGenerations() {
        LocalFileSnowflakeAssembler assembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 1, assembler, properties);
        generator.init();

        int count = 5000;
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }
        assertEquals(count, ids.size(), "所有ID应该唯一");
    }

    @Test
    void testSaveAndRelease() {
        LocalFileSnowflakeAssembler assembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 1, assembler, properties);
        generator.init();

        generator.nextId();
        generator.nextId();

        generator.saveAndRelease();

        assertTrue(assembler.isReleased());
    }

    @Test
    void testLastRunHistoryPersistence() {
        LocalFileSnowflakeAssembler assembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 1, assembler, properties);
        generator.init();

        generator.nextId();
        generator.nextId();
        generator.saveAndRelease();

        LocalFileSnowflakeAssembler newAssembler = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        String history = newAssembler.getLastRunHistory();

        assertNotNull(history);
        assertTrue(history.contains("|"));
    }

    @Test
    void testDifferentWorkstationIds() {
        LocalFileSnowflakeAssembler assembler1 = new LocalFileSnowflakeAssembler(0, 1, "8080", tempDir.toString());
        LocalFileSnowflakeAssembler assembler2 = new LocalFileSnowflakeAssembler(0, 2, "8080", tempDir.toString());

        SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(0, 1, assembler1, properties);
        SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(0, 2, assembler2, properties);
        gen1.init();
        gen2.init();

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();

        assertNotEquals(id1, id2, "不同工作站ID生成的ID应该不同");
    }
}