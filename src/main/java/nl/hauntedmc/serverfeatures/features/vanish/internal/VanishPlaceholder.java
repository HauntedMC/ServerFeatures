package nl.hauntedmc.serverfeatures.features.vanish.internal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VanishPlaceholder extends PlaceholderExpansion {

    private final Vanish feature;

    public VanishPlaceholder(Vanish feature) {
        this.feature = feature;
    }

    @Override public @NotNull String getIdentifier() { return "vanish"; }
    @Override public @NotNull String getAuthor()     { return "HauntedMC"; }
    @Override public @NotNull String getVersion()    { return "1.0"; }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("playercount")) {
            try {
                int online   = Bukkit.getOnlinePlayers().size();
                int vanished = feature.getService() != null ? feature.getService().countVanished() : 0;
                int adjusted = Math.max(0, online - vanished);
                return Integer.toString(adjusted);
            } catch (Throwable t) {
                return "0";
            }
        }
        return null;
    }
}
