package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public class MessageUtils {
    public static void sendTitle(Player paramPlayer, String paramString) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(paramString);
        paramPlayer.sendActionBar(component);
    }
}
