package nl.hauntedmc.serverfeatures.api.util.text.format;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentFormatterTest {

    @Test
    void sanitizerRemovesDisallowedTagsButKeepsInnerText() {
        Component component = ComponentFormatter.deserialize("<click:run_command:/op>Hi</click> <red>There</red>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.Feature.COLORS)
                .sanitizeUnknownTags(true)
                .strict(true)
                .toComponent();

        String plain = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();

        assertEquals("Hi There", plain);
    }

    @Test
    void strictModeWithoutSanitizerStillPreservesText() {
        Component component = ComponentFormatter.deserialize("<custom>abc</custom>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.Feature.COLORS)
                .sanitizeUnknownTags(false)
                .strict(true)
                .toComponent();

        String plain = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
        assertEquals("<custom>abc</custom>", plain);
    }

    @Test
    void autoLinkWrapsUrlsAsClickEvents() {
        Component component = ComponentFormatter.deserialize("Visit www.example.com")
                .expect(TextFormatter.InputFormat.PLAIN)
                .features(ComponentFormatter.Feature.CLICK, ComponentFormatter.Feature.DECORATIONS)
                .autoLinkUrls(true)
                .toComponent();

        String mini = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                .build();
        String json = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.JSON)
                .build();

        assertTrue(mini.contains("www.example.com"));
        assertTrue(json.contains("open_url"));
        assertTrue(json.contains("https://www.example.com"));
    }

    @Test
    void preprocessIsAppliedBeforeParsing() {
        Component component = ComponentFormatter.deserialize("Hello %name%")
                .expect(TextFormatter.InputFormat.PLAIN)
                .preprocess(s -> s.replace("%name%", "Remy"))
                .toComponent();

        String plain = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();

        assertEquals("Hello Remy", plain);
    }

    @Test
    void serializerSupportsPrettyJsonAndLegacyFormats() {
        Component component = Component.text("Test");

        String plain = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
        String prettyJson = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.JSON)
                .jsonPretty(true)
                .build();
        String compactJson = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.JSON)
                .build();
        String legacy = ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.LEGACY_AMPERSAND)
                .legacyOptions(ComponentFormatter.Serializer.LegacyOptions.ampersand())
                .build();

        assertEquals("Test", plain);
        assertTrue(prettyJson.length() >= compactJson.length());
        assertTrue(prettyJson.contains("Test"));
        assertTrue(legacy.contains("Test"));
    }

    @Test
    void allDefaultsIncludesCoreFormattingFeatures() {
        var defaults = ComponentFormatter.ALL_DEFAULTS();

        assertTrue(defaults.contains(ComponentFormatter.Feature.COLORS));
        assertTrue(defaults.contains(ComponentFormatter.Feature.CLICK));
        assertTrue(defaults.contains(ComponentFormatter.Feature.HOVER));
    }
}
