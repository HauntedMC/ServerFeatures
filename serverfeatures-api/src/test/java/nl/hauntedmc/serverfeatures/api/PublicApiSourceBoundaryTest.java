package nl.hauntedmc.serverfeatures.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicApiSourceBoundaryTest {

    private static final Set<String> ROOT_FILES = Set.of(
            "ApiFailureCode.java",
            "ApiOperationException.java",
            "AsyncContract.java",
            "RuntimeState.java",
            "ServerFeaturesApi.java",
            "ServerFeaturesApiVersion.java"
    );

    @Test
    void apiContainsOnlyStablePublicContracts() throws IOException {
        Path root = Path.of("src/main/java/nl/hauntedmc/serverfeatures/api");
        List<String> forbidden = Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(root::relativize)
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .filter(PublicApiSourceBoundaryTest::isForbidden)
                .sorted()
                .toList();

        assertEquals(List.of(), forbidden,
                "serverfeatures-api contains runtime/toolkit/legacy sources: " + forbidden);
    }

    private static boolean isForbidden(String path) {
        if (!path.contains("/")) {
            return !ROOT_FILES.contains(path);
        }
        if (path.startsWith("feature/")) {
            return path.startsWith("feature/meta/") || path.equals("feature/Feature.java");
        }
        if (path.startsWith("service/")) {
            return false;
        }
        return !path.startsWith("capability/");
    }
}
