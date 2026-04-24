package com.mars.linker.broker.netty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRetainStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistAndReloadRetainedMessages() {
        Path file = tempDir.resolve("retain.tsv");
        FileRetainStore store = new FileRetainStore(file);
        store.put("a/b", new byte[]{0x01, 0x02}, 1);
        store.put("x/y", new byte[]{0x03}, 0);

        FileRetainStore reloaded = new FileRetainStore(file);
        List<FileRetainStore.RetainedMessage> list = reloaded.list();
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(m -> "a/b".equals(m.topic) && m.qos == 1));
        FileRetainStore.RetainedMessage msg = list.stream().filter(m -> "a/b".equals(m.topic)).findFirst().orElseThrow();
        assertArrayEquals(new byte[]{0x01, 0x02}, msg.payload);
    }

    @Test
    void shouldWriteVersionHeader() throws Exception {
        Path file = tempDir.resolve("retain.tsv");
        FileRetainStore store = new FileRetainStore(file);
        store.put("a/b", new byte[]{0x01}, 0);
        List<String> lines = java.nio.file.Files.readAllLines(file);
        assertEquals("#retain-store\tv1", lines.get(0));
    }

    @Test
    void incompatibleHeaderShouldBeIgnored() throws Exception {
        Path file = tempDir.resolve("retain.tsv");
        java.nio.file.Files.writeString(file, "#retain-store\tv9\na/b\t0\tAQ==\n");
        FileRetainStore store = new FileRetainStore(file);
        assertEquals(0, store.list().size());
    }

    @Test
    void removeShouldDeleteRetainedTopic() {
        Path file = tempDir.resolve("retain.tsv");
        FileRetainStore store = new FileRetainStore(file);
        store.put("topic/1", new byte[]{0x11}, 0);
        store.remove("topic/1");

        FileRetainStore reloaded = new FileRetainStore(file);
        assertEquals(0, reloaded.list().size());
    }

    @Test
    void malformedRowsShouldBeIgnored() throws Exception {
        Path file = tempDir.resolve("retain.tsv");
        java.nio.file.Files.writeString(file,
                "#retain-store\tv1\n" +
                        "ok/topic\t1\tAQI=\n" +
                        "\t1\tAQI=\n" +           // empty topic
                        "bad/qos\tx\tAQI=\n" +    // qos not number
                        "bad/qos2\t3\tAQI=\n" +   // qos out of range
                        "bad/payload\t0\tnot-b64\n");
        FileRetainStore store = new FileRetainStore(file);
        List<FileRetainStore.RetainedMessage> list = store.list();
        assertEquals(1, list.size());
        assertEquals("ok/topic", list.get(0).topic);
    }
}
