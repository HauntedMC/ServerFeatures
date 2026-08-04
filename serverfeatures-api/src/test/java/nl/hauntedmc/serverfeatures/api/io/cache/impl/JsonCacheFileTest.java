package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCacheFileTest {

    @TempDir
    Path tmp;

    @Test
    void putGetFindRemoveAndCleanupExpired() {
        File file = tmp.resolve("json/store.json").toFile();
        JsonCacheFile store = new JsonCacheFile(file);

        CacheValue live = CacheValue.of(Map.of("name", "alex"), System.currentTimeMillis() + 60_000);
        CacheValue expired = CacheValue.of(Map.of("name", "old"), System.currentTimeMillis() - 1);
        store.put("live-user", live);
        store.put("old-user", expired);

        assertEquals("alex", store.get("live-user").getData().get("name"));
        assertEquals(1, store.find("live-.*").size());

        store.cleanupExpired();
        assertNull(store.get("old-user"));
        assertTrue(store.getKeys().contains("live-user"));

        store.remove("live-user");
        assertTrue(store.isEmpty());
    }

    @Test
    void concurrentInstancesPreserveAllEntries() throws Exception {
        File file = tmp.resolve("concurrent/votes.json").toFile();
        int voteCount = 80;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writes = new ArrayList<>();

        try {
            for (int index = 0; index < voteCount; index++) {
                int voteIndex = index;
                writes.add(executor.submit(() -> {
                    start.await();
                    JsonCacheFile store = new JsonCacheFile(file);
                    store.put(
                            "vote-" + voteIndex,
                            CacheValue.of(
                                    Map.of("service", "service-" + voteIndex),
                                    System.currentTimeMillis() + 60_000
                            )
                    );
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> write : writes) {
                write.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }

        JsonCacheFile reloaded = new JsonCacheFile(file);
        assertEquals(voteCount, reloaded.listAll().size());
        for (int index = 0; index < voteCount; index++) {
            assertEquals(
                    "service-" + index,
                    reloaded.get("vote-" + index).getData().get("service")
            );
        }
        assertStrictJson(file.toPath());
    }

    @Test
    void repairsTrailingGarbageAndPreservesOriginal() throws Exception {
        Path file = tmp.resolve("recovery/votes.json");
        Files.createDirectories(file.getParent());
        long expiration = System.currentTimeMillis() + 60_000;
        String key = "vote.b5cfd842-5455-38a2-9c0d-da059d1e39e5";
        Files.writeString(
                file,
                "{\"" + key + "\":{\"value\":{\"service\":\"MinecraftKrant\"},"
                        + "\"expirationTimestamp\":" + expiration + "}}242}}",
                StandardCharsets.UTF_8
        );

        JsonCacheFile recovered = new JsonCacheFile(file.toFile());

        assertEquals("MinecraftKrant", recovered.get(key).getData().get("service"));
        String repairedJson = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(repairedJson.endsWith("242}}"));
        assertStrictJson(file);
        assertTrue(hasCorruptionBackup(file));
    }

    @Test
    void quarantinesUnrecoverableDocumentAndRemainsWritable() throws Exception {
        Path file = tmp.resolve("quarantine/votes.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"vote\":", StandardCharsets.UTF_8);

        JsonCacheFile recovered = new JsonCacheFile(file.toFile());

        assertTrue(recovered.isEmpty());
        recovered.put(
                "replacement",
                CacheValue.of(Map.of("service", "MinecraftKrant"), System.currentTimeMillis() + 60_000)
        );
        assertEquals("MinecraftKrant", recovered.get("replacement").getData().get("service"));
        assertStrictJson(file);
        assertTrue(hasCorruptionBackup(file));
    }

    private static void assertStrictJson(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement parsed = assertDoesNotThrow(() -> new Gson().fromJson(json, JsonElement.class));
        assertTrue(parsed.isJsonObject());
    }

    private static boolean hasCorruptionBackup(Path file) throws Exception {
        String prefix = file.getFileName() + ".corrupt-";
        try (Stream<Path> files = Files.list(file.getParent())) {
            return files.anyMatch(candidate -> candidate.getFileName().toString().startsWith(prefix));
        }
    }
}
