package nl.hauntedmc.serverfeatures.api.util.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.regex.Pattern;

public class ComponentUtils {

    // Accepts &-codes + true hex (#RRGGBB). Deserializes both '#RRGGBB' and '&x&R&R&G&G&B&B'.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR) // same as legacyAmpersand()
            .hexColors()                                         // don't downsample; keep RGB
            .hexCharacter(LegacyComponentSerializer.HEX_CHAR)    // '#'
            .build();

    // Same as above, but when SERIALIZING hex it uses the &x&R&R&G&G&B&B format (Bungee/Spigot).
    // We only use this when the INPUT was already in that format, to preserve round-trip behavior.
    private static final LegacyComponentSerializer LEGACY_X = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR)
            .hexColors()
            .hexCharacter(LegacyComponentSerializer.HEX_CHAR)
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // Detect the Bungee &x hex pattern in the original input
    private static final Pattern BUNGEE_HEX = Pattern.compile("(?i)&x(?:&[0-9a-f]){6}");

    public static Component deserializeMultilineComponent(String text) {
        return Component.join(
                JoinConfiguration.separator(Component.newline()),
                // split with limit=-1 to preserve trailing empty lines
                Arrays.stream(text.split("<newline>", -1))
                        .map(LEGACY::deserialize)
                        .toList()
        );
    }

    public static Component deserializeComponent(String text) {
        if (text.contains("<newline>")) {
            return deserializeMultilineComponent(text);
        }
        return LEGACY.deserialize(text);
    }

    public static Component deserializeMMComponent(String text) {
        MiniMessage textSerializer = MiniMessage.builder().tags(TagResolver.builder()
                        .resolver(StandardTags.color())
                        .resolver(StandardTags.decorations())
                        .resolver(StandardTags.clickEvent())
                        .resolver(StandardTags.hoverEvent())
                        .resolver(StandardTags.reset())
                        .resolver(StandardTags.gradient())
                        .resolver(StandardTags.newline())
                        .resolver(StandardTags.transition())
                        .resolver(StandardTags.shadowColor())
                        .build())
                .build();

        return textSerializer.deserialize(text);
    }

    /**
     * Normalizes a legacy string. If the input used the Bungee &x hex form, we keep that on output;
     * otherwise (plain &-codes or '#RRGGBB') we output using '#RRGGBB' for hex and standard &-codes.
     */
    public static String serializeLegacyString(String message) {
        // Always deserialize with LEGACY (handles &-codes, '#RRGGBB', and '&x' form)
        Component c = LEGACY.deserialize(message);

        // Preserve '&x' style only if the input used it; otherwise use the default serializer
        LegacyComponentSerializer out = BUNGEE_HEX.matcher(message).find() ? LEGACY_X : LEGACY;
        return out.serialize(c);
    }
}
