package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSanitizeUtilTest {

    @Test
    void toLinkedMapDeepCopiesNestedMapsAndLists() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", Map.of("b", 1));
        input.put("c", List.of("x", "y"));

        LinkedHashMap<String, Object> out = YamlSanitizeUtil.toLinkedMap(input);

        assertEquals(2, out.size());
        assertTrue(out.get("a") instanceof Map<?, ?>);
        assertTrue(out.get("c") instanceof List<?>);
        assertTrue(out.get("c") instanceof ArrayList<?>);
    }

    @Test
    void setCreatesIntermediateSections() {
        Map<String, Object> root = new LinkedHashMap<>();

        YamlSanitizeUtil.set(root, "one.two.three", 7);

        Map<?, ?> one = (Map<?, ?>) root.get("one");
        Map<?, ?> two = (Map<?, ?>) one.get("two");
        assertEquals(7, two.get("three"));
    }

    @Test
    void ensureExactListOnlyReplacesWhenNecessary() {
        Map<String, Object> root = new LinkedHashMap<>();
        YamlSanitizeUtil.set(root, "k.list", List.of("a", "b"));

        YamlSanitizeUtil.ensureExactList(root, "k.list", List.of("a", "b"));
        Object afterSame = ((Map<?, ?>) root.get("k")).get("list");
        assertEquals(List.of("a", "b"), afterSame);

        YamlSanitizeUtil.ensureExactList(root, "k.list", List.of("a", "c"));
        Object afterChange = ((Map<?, ?>) root.get("k")).get("list");
        assertEquals(List.of("a", "c"), afterChange);
    }

    @Test
    void normalizeRemovesTrailingWhitespaceAcrossLineEndings() {
        String in = "line1\r\nline2  \r\n\r\n";

        String normalized = YamlSanitizeUtil.normalize(in);

        assertEquals("line1\nline2", normalized);
    }

    @Test
    void appendControlCommentsMarksOnlyControlledPaths() {
        String dumped = """
                settings:
                  key1: value1
                  key2: value2
                aliases: now-in-commands.yml
                """;

        String out = YamlSanitizeUtil.appendControlComments(
                dumped,
                List.of("settings.key2", "aliases")
        );

        assertTrue(out.contains("key2: value2 # controlled by Sanitize"));
        assertTrue(out.contains("aliases: now-in-commands.yml # controlled by Sanitize"));
        assertTrue(!out.contains("key1: value1 # controlled by Sanitize"));
    }
}
