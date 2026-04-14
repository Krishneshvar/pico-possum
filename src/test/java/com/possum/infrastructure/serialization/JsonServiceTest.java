package com.possum.infrastructure.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonServiceTest {

    @TempDir
    Path tempDir;

    private JsonService jsonService;

    @BeforeEach
    void setUp() {
        jsonService = new JsonService();
    }

    @Test
    void toJson_validObject_returnsJsonString() {
        TestObject obj = new TestObject("name", 25);
        String json = jsonService.toJson(obj);
        
        assertTrue(json.contains("\"name\":\"name\""));
        assertTrue(json.contains("\"age\":25"));
    }

    @Test
    void fromJson_validJson_returnsObject() {
        String json = "{\"name\":\"test\",\"age\":30}";
        TestObject result = jsonService.fromJson(json, TestObject.class);
        
        assertNotNull(result);
        assertEquals("test", result.name());
        assertEquals(30, result.age());
    }

    @Test
    void writeAndRead_filesystem_worksCorrectly() throws IOException {
        Path file = tempDir.resolve("test.json");
        TestObject original = new TestObject("FileTest", 100);
        
        jsonService.write(file, original);
        assertTrue(java.nio.file.Files.exists(file));
        
        TestObject read = jsonService.read(file, TestObject.class);
        assertEquals(original.name(), read.name());
        assertEquals(original.age(), read.age());
    }

    @Test
    void read_fileNotExists_returnsNull() {
        Path missing = tempDir.resolve("missing.json");
        assertNull(jsonService.read(missing, TestObject.class));
    }

    @Test
    void write_createsDirectories() throws IOException {
        Path deepFile = tempDir.resolve("sub/dir/test.json");
        TestObject obj = new TestObject("Deep", 1);
        
        jsonService.write(deepFile, obj);
        assertTrue(java.nio.file.Files.exists(deepFile));
    }

    @Test
    void complexObjects_serializeCorrectly() {
        Map<String, List<Integer>> complex = Map.of("data", List.of(1, 2, 3));
        String json = jsonService.toJson(complex);
        
        assertTrue(json.contains("[1,2,3]"));
        
        Map<?, ?> result = jsonService.fromJson(json, Map.class);
        assertEquals(1, result.size());
    }

    @Test
    void fromJson_invalidJson_throwsException() {
        assertThrows(IllegalStateException.class, () -> jsonService.fromJson("{invalid", TestObject.class));
    }

    public record TestObject(String name, int age) {}
}
