package nl.hauntedmc.serverfeatures.features.tablist.internal.hook;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import org.bukkit.entity.Player;

/**
 * Utility class to create custom nametag components using localized placeholders.
 * <p>
 * This class should be initialized once with a {@link Tablist} feature instance.
 * After that, you can access it statically via {@link #getInstance()}.
 */
public final class PlaceholderHook {

    private final Tablist feature;
    private static PlaceholderHook instance;

    /**
     * Initializes the PlaceholderHook with the given feature.
     * Should be called once during your feature's initialization.
     *
     * @param feature the Tablist feature instance
     */
    public PlaceholderHook(Tablist feature) {
        this.feature = feature;
        instance = this;
    }

    /**
     * Gets the singleton instance of the PlaceholderHook.
     *
     * @return the PlaceholderHook instance
     * @throws IllegalStateException if the instance has not been initialized yet!
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
     * the feature's localization handler, then combines them into one component.
     */
    public Component getTablistName(Player player) {
        Component prefix = feature.getLocalizationHandler().getMessage("tablist.prefix", player);
        Component playerName = feature.getLocalizationHandler().getMessage("tablist.playername", player);
        Component suffix = feature.getLocalizationHandler().getMessage("tablist.suffix", player);

        return Component.empty()
                .append(prefix)
                .append(playerName)
                .append(suffix);
    }

}
