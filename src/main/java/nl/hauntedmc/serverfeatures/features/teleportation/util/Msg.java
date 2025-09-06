package nl.hauntedmc.serverfeatures.features.teleportation.util;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class Msg {
    private Msg() {}

    public static void send(Teleportation feature, CommandSender audience, String key, Map<String, String> placeholders) {
        var msg = feature.getLocalizationHandler().getMessage(key);
        audience.sendMessage(msg.forAudience(audience).withPlaceholders(placeholders).build());
    }
}
