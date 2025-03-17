package nl.hauntedmc.serverfeatures.features.tablist.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.util.CastUtils;
import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

/**
 * Central handler for all tablist-related logic.
 */
public class TablistHandler {

    private final Tablist feature;
    private final Permission permission;
    private final Map<String, Integer> rankPriorityMap = new HashMap<>();

    /**
     * Construct a TablistHandler with a reference to the Tablist feature.
     * Also initializes Vault's Permission provider and loads rank ordering from config.
     *
     * @param feature the main Tablist feature instance
     */
    public TablistHandler(Tablist feature) {
        this.feature = feature;
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager()
                .getRegistration(Permission.class);
        this.permission = (rsp != null) ? rsp.getProvider() : null;
        loadRankPriorityMap();
    }

    /**
     * Loads the rank ordering from the configuration.
     * If the config is missing or invalid, a default ordering is used.
     */
    private void loadRankPriorityMap() {
        List<String> rankOrderSetting = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("rank_order"), String.class);

        for (int i = 0; i < rankOrderSetting.size(); i++) {
            String rank = rankOrderSetting.get(i).toLowerCase().trim();
            rankPriorityMap.put(rank, i + 1);
        }
    }

    /**
     * Updates the tablist for all currently online players by setting their correct order.
     */
    public void refreshAllPlayers() {
        List<Player> sortedPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Sort players by rank priority first, then by name
        sortedPlayers.sort(Comparator
                .comparingInt((Player p) -> getRankPriority(getRank(p))) // Sort by rank priority (low = high)
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
                .reversed());

        // Assign each player their position in the ordered list
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            player.setPlayerListOrder(i); // Assign position as order index
            updateTablist(player);
        }
    }

    /**
     * Update the header/footer and tab list name for the given player.
     */
    public void updateTablist(Player player) {
        Component header = feature.getLocalizationHandler().getMessage("tablist.header", player);
        Component footer = feature.getLocalizationHandler().getMessage("tablist.footer", player);
        player.sendPlayerListHeaderAndFooter(header, footer);
        updateTablistName(player);
    }

    /**
     * Updates the player's tab list name.
     */
    public void updateTablistName(Player player) {
        player.playerListName(getTablistName(player));
    }

    /**
     * Clears the tablist header/footer for the given player.
     */
    public void clearTablist(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    /**
     * Convenience method: clears then updates the player's tablist.
     */
    public void initTablist(Player player) {
        clearTablist(player);
        updateTablist(player);
    }

    /**
     * Combines localized prefix, player name, and suffix into a custom tablist name component.
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

    /**
     * Retrieves the player's rank using Vault's Permission API.
     *
     * @param player the player whose rank is being queried
     * @return the player's primary group as a lowercase string; defaults to "default" if not found.
     */
    private String getRank(Player player) {
        if (permission != null) {
            String group = permission.getPrimaryGroup(player);
            return (group != null && !group.isEmpty()) ? group.toLowerCase() : "default";
        }
        return "default";
    }

    /**
     * Retrieves the rank priority for sorting.
     * Lower numbers indicate higher priority.
     *
     * @param rank the player's rank
     * @return the priority value from the configuration or a default value if the rank isn't configured.
     */
    private int getRankPriority(String rank) {
        return rankPriorityMap.getOrDefault(rank.toLowerCase(), rankPriorityMap.getOrDefault("default", rankPriorityMap.size() + 1));
    }
}
