package nl.hauntedmc.serverfeatures.framework;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the ServerFeatures feature boundary.
 *
 * <p>Feature implementations may collaborate through public capabilities or framework-owned internal ports,
 * but must never import/reference another feature implementation package directly. This keeps feature lifecycle,
 * reload and capability generations independent instead of creating hidden implementation coupling.</p>
 */
class FeatureIsolationArchitectureTest {

    private static final Path FEATURES_SOURCE = Path.of(
            "src/main/java/nl/hauntedmc/serverfeatures/features");
    private static final Pattern FEATURE_REFERENCE = Pattern.compile(
            "nl\\.hauntedmc\\.serverfeatures\\.features\\.([a-z][a-z0-9_]*)\\.");

    @Test
    void featuresDoNotReferenceOtherFeatureImplementations() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(FEATURES_SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relative = FEATURES_SOURCE.relativize(file);
                if (relative.getNameCount() < 2) {
                    continue;
                }

                String ownerFeature = relative.getName(0).toString();
                String source = Files.readString(file);
                Matcher matcher = FEATURE_REFERENCE.matcher(source);
                while (matcher.find()) {
                    String referencedFeature = matcher.group(1);
                    if (!ownerFeature.equals(referencedFeature)) {
                        violations.add(relative + " -> " + referencedFeature);
                    }
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Direct feature-to-feature implementation references are forbidden; use a public capability "
                        + "or framework-owned internal port instead:\n  " + String.join("\n  ", violations));
    }
}
