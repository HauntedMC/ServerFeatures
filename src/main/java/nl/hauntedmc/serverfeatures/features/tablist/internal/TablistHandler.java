package nl.hauntedmc.serverfeatures.features.tablist.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import nl.hauntedmc.serverfeatures.features.tablist.internal.hook.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Central handler for all tablist-related logic.
 */
public class TablistHandler {

    private final Tablist feature;

    /**
     * Construct a TablistHandler with a reference to the Tablist feature.
     *
     * @param feature the main Tablist feature instance
     */
    public TablistHandler(Tablist feature) {
        this.feature = feature;
    }

    /**
     * Update the header/footer for the given player.
     */
    public void updateTablist(Player player) {
        Component header = feature.getLocalizationHandler().getMessage("tablist.header", player);
        Component footer = feature.getLocalizationHandler().getMessage("tablist.footer", player);
        player.sendPlayerListHeaderAndFooter(header, footer);
        updateTablistName(player);
    }

    public void updateTablistName(Player player) {
        player.playerListName(PlaceholderHook.getInstance().getTablistName(player));
    }

    /**
     * Clear the tablist header/footer for the given player.
     */
    public void clearTablist(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    /**
     * Convenience method: clears + updates the player's tablist immediately,
     * and also sets their tab name (using PlaceholderHook if available).
     */
    public void initTablist(Player player) {
        clearTablist(player);
        updateTablist(player);
    }

    /**
     * Update the tablist (header/footer) for all currently online players.
     * This does NOT update their tab names by default. If you want to do so,
     * simply call setPlayerTabName(...) or replicate that logic here.
     */
    public void refreshAllPlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            updateTablist(online);
        }
    }

}
