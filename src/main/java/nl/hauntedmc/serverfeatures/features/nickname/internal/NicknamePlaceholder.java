package nl.hauntedmc.serverfeatures.features.nickname.internal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class NicknamePlaceholder extends PlaceholderExpansion {

    private final Nickname feature;

    public NicknamePlaceholder(Nickname feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverfeatures";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HauntedMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!params.equalsIgnoreCase("nickname")) {
            return null;
        }
        if (player == null) {
            return "";
        }

        Optional<String> nickname = feature.getNicknameHandler().getNickname(player);
        if (nickname.isPresent()) {
            return nickname.get();
        }

        feature.getNicknameHandler().warmNicknameIntoCache(player).exceptionally(exception -> {
            feature.getLogger().warning("Could not warm nickname placeholder for " + player.getUniqueId() + ": "
                    + exception.getMessage());
            return Optional.empty();
        });
        return player.getName();
    }
}
