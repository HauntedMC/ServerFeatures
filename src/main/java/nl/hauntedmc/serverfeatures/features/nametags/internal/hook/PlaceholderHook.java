package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import org.bukkit.entity.Player;

public final class PlaceholderHook {
    private final Nametags feature;
    private static PlaceholderHook instance;

    public PlaceholderHook(Nametags feature) {
        this.feature = feature;
        instance = this;
    }

    public static PlaceholderHook getInstance() {
        if (instance == null) throw new IllegalStateException("PlaceholderHook not initialized");
        return instance;
    }

    public Component getNametagText(Player player) {
        Component prefix = feature.getLocalizationHandler().getMessage("nametags.prefix").forAudience(player).build();
        Component suffix = feature.getLocalizationHandler().getMessage("nametags.suffix").forAudience(player).build();
        Component playerName = feature.getLocalizationHandler().getMessage("nametags.playername").forAudience(player).build();
        return Component.empty().append(prefix).append(playerName).append(suffix);
    }
}
