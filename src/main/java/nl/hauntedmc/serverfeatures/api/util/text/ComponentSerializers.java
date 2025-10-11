package nl.hauntedmc.serverfeatures.api.util.text;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ComponentSerializers {

    public static LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER_AMP = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .extractUrls()
            .hexColors()
            .build();

    public static LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexCharacter('#')
            .extractUrls()
            .hexColors()
            .build();

}
