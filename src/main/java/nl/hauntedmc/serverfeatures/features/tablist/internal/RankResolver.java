package nl.hauntedmc.serverfeatures.features.tablist.internal;

import org.bukkit.entity.Player;

interface RankResolver {
    String getRank(Player player);
    boolean isReady();
}
