package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.entity.Player;

/**
 * Builds the configured nametag component.
 */
public final class PlaceholderHook {
    private static volatile PlaceholderHook instance;

    private final Nametags feature;

    public PlaceholderHook(Nametags feature) {
        this.feature = feature;
        instance = this;
    }

    public static PlaceholderHook getInstance() {
        PlaceholderHook current = instance;
        if (current == null) {
            throw new IllegalStateException("PlaceholderHook has not been initialized yet.");
        }
        return current;
    }

    public Component getNametagText(Player player) {
        try {
            Component prefix = feature.getLocalizationHandler()
                    .getMessage("nametags.prefix")
                    .forAudience(player)
                    .build();
            Component suffix = feature.getLocalizationHandler()
                    .getMessage("nametags.suffix")
                    .forAudience(player)
                    .build();
            Component playerName = feature.getLocalizationHandler()
                    .getMessage("nametags.playername")
                    .forAudience(player)
                    .build();

            return Component.empty()
                    .append(prefix)
                    .append(playerName)
                    .append(suffix);
        } catch (RuntimeException | LinkageError exception) {
            feature.getLogger().warning(
                    "Kon nametag placeholders niet verwerken voor " + player.getName()
                            + "; de spelersnaam wordt als fallback gebruikt: " + rootMessage(exception)
            );
            return Component.text(player.getName());
        }
    }

    public void close() {
        if (instance == this) {
            instance = null;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
