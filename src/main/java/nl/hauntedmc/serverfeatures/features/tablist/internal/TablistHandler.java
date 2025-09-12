package nl.hauntedmc.serverfeatures.features.tablist.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.serverfeatures.features.tablist.Tablist;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Central handler for all tablist-related logic.
 */
public class TablistHandler {

    private final Tablist feature;
    private final Map<String, Integer> rankPriorityMap = new HashMap<>();
    private final RankResolver rankResolver;
    private final boolean vaultAvailable;

    public TablistHandler(Tablist feature) {
        this.feature = feature;

        boolean hasVault = Bukkit.getPluginManager().isPluginEnabled("Vault");
        RankResolver resolver;
        if (hasVault) {
            RankResolver vr = new VaultRankResolver();
            resolver = vr.isReady() ? vr : new AlphaRankResolver();
        } else {
            resolver = new AlphaRankResolver();
        }
        this.rankResolver = resolver;
        this.vaultAvailable = resolver.isReady();

        loadRankPriorityMap();
    }

    /**
     * Loads the rank ordering from the configuration.
     */
    private void loadRankPriorityMap() {
        List<String> rankOrderSetting = CastUtils.safeCastToList(
                feature.getConfigHandler().getSetting("rank_order"), String.class
        );
        for (int i = 0; i < rankOrderSetting.size(); i++) {
            String rank = rankOrderSetting.get(i).toLowerCase().trim();
            rankPriorityMap.put(rank, i + 1);
        }
    }

    /**
     * Updates the tablist for all currently online players by setting their correct order.
     * - With Vault: rank priority (low = high), then name, then reversed() (keeps your original behavior)
     * - Without Vault: alphabetical A→Z
     */
    public void refreshAllPlayers() {
        List<Player> sortedPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (vaultAvailable) {
            sortedPlayers.sort(Comparator
                    .comparingInt((Player p) -> getRankPriority(rankResolver.getRank(p)))
                    .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
                    .reversed());
        } else {
            sortedPlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        }

        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            player.setPlayerListOrder(i);
            updateTablist(player);
        }
    }

    /**
     * Update the header/footer and tab list name for the given player.
     */
    public void updateTablist(Player player) {
        Component header = feature.getLocalizationHandler().getMessage("tablist.header").forAudience(player).build();
        Component footer = feature.getLocalizationHandler().getMessage("tablist.footer").forAudience(player).build();
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
    public void forceRefreshTablist(Player player) {
        clearTablist(player);
        refreshAllPlayers();
    }

    /**
     * Combines localized prefix, player name, and suffix into a custom tablist name component.
     */
    public Component getTablistName(Player player) {
        Component prefix = feature.getLocalizationHandler().getMessage("tablist.prefix").forAudience(player).build();
        Component playerName = feature.getLocalizationHandler().getMessage("tablist.playername").forAudience(player).build();
        Component suffix = feature.getLocalizationHandler().getMessage("tablist.suffix").forAudience(player).build();

        return Component.empty()
                .append(prefix)
                .append(playerName)
                .append(suffix);
    }

    /**
     * Retrieves the rank priority for sorting.
     * Lower numbers indicate higher priority.
     */
    private int getRankPriority(String rank) {
        return rankPriorityMap.getOrDefault(rank.toLowerCase(), rankPriorityMap.getOrDefault("default", rankPriorityMap.size() + 1));
    }
}
