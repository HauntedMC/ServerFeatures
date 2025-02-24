package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.entity.Player;


/**
 * Utility class to create custom nametag components using localized placeholders.
 * <p>
 * This class should be initialized once with a {@link Nametags} feature instance.
 * After that, it can be accessed statically via {@link #getInstance()}.
 */
public final class PlaceholderHook {

    private final Nametags feature;
    private static PlaceholderHook instance;

    /**
     * Initializes the PlaceholderHook with the given feature.
     * Should be called once during your feature's initialization.
     *
     * @param feature the Nametags feature instance.
     */
    public PlaceholderHook(Nametags feature) {
        this.feature = feature;
        instance = this;
    }

    /**
     * Gets the singleton instance of the PlaceholderHook.
     *
     * @return the PlaceholderHook instance.
     * @throws IllegalStateException if the instance has not been initialized.
     */
    public static PlaceholderHook getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PlaceholderHook has not been initialized yet!");
        }
        return instance;
    }

    /**
     * Builds a custom name component for the given player.
     * It retrieves localized strings for prefix, player name, and suffix using
     * the feature's localization handler, converts legacy color codes to components,
     * and combines them.
     *
     * @param player the player to build the custom name for.
     * @return the combined custom name as an Adventure {@link Component}.
     */
    public Component getNametagText(Player player) {
        Component prefix = feature.getLocalizationHandler().getMessage("nametags.prefix", player);
        Component suffix = feature.getLocalizationHandler().getMessage("nametags.suffix", player);
        Component playerName = feature.getLocalizationHandler().getMessage("nametags.playername", player);

        return Component.empty()
                .append(prefix)
                .append(playerName)
                .append(suffix);
    }

}
