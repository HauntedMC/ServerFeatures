package nl.hauntedmc.serverfeatures.features.afk.internal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.util.text.ComponentCodec;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AfkPlaceholder extends PlaceholderExpansion {
    private final AFK feature;
    public AfkPlaceholder(AFK feature) { this.feature = feature; }

    @Override public @NotNull String getIdentifier() { return "afk"; }
    @Override public @NotNull String getAuthor() { return "HauntedMC"; }
    @Override public @NotNull String getVersion() { return "1.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        boolean isAfk = false;
        try {
            Player online = player != null ? Bukkit.getPlayer(player.getUniqueId()) : null;
            isAfk = online != null && online.isOnline() && feature.getService().isAfk(online.getUniqueId());
        } catch (Throwable ignored) {}

        if (params.equalsIgnoreCase("boolean")) return Boolean.toString(isAfk);
        if (params.equalsIgnoreCase("binary")) return isAfk ? "1" : "0";
        if (params.equalsIgnoreCase("formatted")) {
            String key = isAfk ? "afk.placeholder.afk" : "afk.placeholder.not_afk";
            try {
                Object audience = (player != null && player.getPlayer() != null) ? player.getPlayer() : Bukkit.getConsoleSender();
                Component comp = feature.getLocalizationHandler().getMessage(key).forAudience((Audience) audience).build();
                return ComponentCodec.serialize(comp).format(ComponentCodec.Serializer.Format.LEGACY_AMPERSAND).build();
            } catch (Throwable t) {
                return isAfk ? "AFK" : "Active";
            }
        }
        return null;
    }
}
