package nl.hauntedmc.serverfeatures.api.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileTest {

    @TempDir
    Path tmp;

    @Test
    void mutateSetReloadAndRawAccessWork() throws Exception {
        Path file = tmp.resolve("test.yml");
        Files.createFile(file);

        YamlFile yamlFile = new YamlFile(file, Logger.getLogger("yaml-test"));
        assertNotNull(yamlFile.lock());

        yamlFile.mutateAndSave(cfg -> cfg.set("a.b", 5));
        assertEquals(5, yamlFile.getRaw("a.b"));
        assertTrue(yamlFile.contains("a.b"));

        yamlFile.setRawAndSave("a.b", 9);
        assertEquals(9, yamlFile.getRaw("a.b"));

        yamlFile.reload();
        assertEquals(9, yamlFile.snapshotUnsafe().get("a.b"));
    }
}
