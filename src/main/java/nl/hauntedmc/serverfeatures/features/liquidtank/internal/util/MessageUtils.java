package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.text.ComponentCodec;
import nl.hauntedmc.serverfeatures.api.util.text.TextCodec;
import org.bukkit.entity.Player;

public class MessageUtils {
    public static void sendTitle(Player paramPlayer, String paramString) {
        Component component = ComponentCodec.deserialize(paramString).expect(TextCodec.Input.LEGACY_AMPERSAND).features(ComponentCodec.Feature.COLORS).toComponent();

        paramPlayer.sendActionBar(component);
    }
}
