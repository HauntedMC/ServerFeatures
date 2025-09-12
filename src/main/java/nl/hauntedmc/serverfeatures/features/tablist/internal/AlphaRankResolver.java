package nl.hauntedmc.serverfeatures.features.tablist.internal;

import org.bukkit.entity.Player;

final class AlphaRankResolver implements RankResolver {
    @Override public String getRank(Player player) { return "default"; }
    @Override public boolean isReady() { return false; }
}
