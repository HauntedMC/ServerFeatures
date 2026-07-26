package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import org.bukkit.entity.Player;

public class MessageUtils {
    public static void sendActionbar(Player paramPlayer, String paramString) {
        Component component = ComponentFormatter.deserialize(paramString).expect(TextFormatter.InputFormat.LEGACY_AMPERSAND).features(ComponentFormatter.Feature.COLORS).toComponent();

        paramPlayer.sendActionBar(component);
    }
}
