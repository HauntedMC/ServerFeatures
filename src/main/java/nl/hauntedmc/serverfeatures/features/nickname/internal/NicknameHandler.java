package nl.hauntedmc.serverfeatures.features.nickname.internal;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.api.util.type.CastUtils;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import nl.hauntedmc.serverfeatures.features.nickname.internal.service.NicknameService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class NicknameHandler {

    private final NicknameService nicknameService;
    private final Map<UUID, String> nicknameCache = new ConcurrentHashMap<>();
    private final Nickname feature;
    private final int minNicknameLength;
    private final int maxNicknameLength;
    private final List<String> allowedCharacters;
    private final List<String> disallowedFormatting;

    public NicknameHandler(Nickname feature) {
        this.feature = feature;
        this.nicknameService = new NicknameService(feature);
        this.maxNicknameLength = (int) feature.getConfigHandler().get("maxNicknameLength");
        this.minNicknameLength = (int) feature.getConfigHandler().get("minNicknameLength");
        this.allowedCharacters = CastUtils.safeCastToList(
                feature.getConfigHandler().get("allowedCharacters"),
                String.class
        );
        this.disallowedFormatting = CastUtils.safeCastToList(
                feature.getConfigHandler().get("disallowedFormatting"),
                String.class
        );
    }

    private static @NotNull String translateColours(String unformattedNickname) {
        return TextFormatter.convert(unformattedNickname)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .toMiniMessage();
    }

    public Optional<String> getNickname(OfflinePlayer player) {
        return Optional.ofNullable(nicknameCache.get(player.getUniqueId()));
    }

    public void loadNicknameIntoCache(Player player) {
        UUID playerId = player.getUniqueId();
        nicknameService.findPlayerIdentity(playerId).whenComplete((identity, throwable) -> {
            if (throwable != null || identity == null || identity.isEmpty()) {
                nicknameCache.remove(playerId);
                return;
            }
            loadNicknameIntoCache(playerId, identity.get());
        });
    }

    public void loadNicknameIntoCache(Player player, PlayerIdentity identity) {
        loadNicknameIntoCache(player.getUniqueId(), identity);
    }

    public CompletionStage<Optional<String>> warmNicknameIntoCache(OfflinePlayer player) {
        if (player == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        UUID playerId = player.getUniqueId();
        Optional<String> cached = Optional.ofNullable(nicknameCache.get(playerId));
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return nicknameService.findPlayerIdentity(playerId)
                .thenCompose(identity -> identity
                        .map(nicknameService::findNickname)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .thenApply(nickname -> {
                    nickname.ifPresentOrElse(
                            value -> nicknameCache.put(playerId, value),
                            () -> nicknameCache.remove(playerId)
                    );
                    return nickname;
                });
    }

    private void loadNicknameIntoCache(UUID playerId, PlayerIdentity identity) {
        nicknameService.findNickname(identity).whenComplete((nickname, throwable) -> {
            if (throwable != null || nickname == null || nickname.isEmpty()) {
                nicknameCache.remove(playerId);
                return;
            }
            nicknameCache.put(playerId, nickname.get());
        });
    }

    public boolean setNickname(Player player, String unformattedNickname) {
        for (String disallowed : disallowedFormatting) {
            if (unformattedNickname.contains(disallowed)) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("nickname.disallowed_formatting")
                        .forAudience(player)
                        .build());
                return false;
            }
        }
        String nickname = translateColours(unformattedNickname);
        String plainTextNickname = TextFormatter.toPlain(nickname);

        if (!hasValidNicknameLength(plainTextNickname)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("nickname.max_length_exceeded")
                    .forAudience(player)
                    .build());
            return false;
        }

        if (!hasValidNicknameCharacters(plainTextNickname)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("nickname.invalid_characters")
                    .forAudience(player)
                    .build());
            return false;
        }

        Optional<PlayerIdentity> playerIdentity = nicknameService.getCachedPlayerIdentity(player);
        if (playerIdentity.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("nickname.data_unavailable")
                    .forAudience(player)
                    .build());
            return false;
        }

        nicknameService.setNickname(playerIdentity.get(), nickname);
        nicknameCache.put(player.getUniqueId(), nickname);
        return true;
    }

    private boolean hasValidNicknameLength(String plainTextNickname) {
        return plainTextNickname.length() <= maxNicknameLength && plainTextNickname.length() >= minNicknameLength;
    }

    private boolean hasValidNicknameCharacters(String plainTextNickname) {
        for (int index = 0; index < plainTextNickname.length(); index++) {
            char character = plainTextNickname.charAt(index);
            if (!Character.isLetterOrDigit(character) && !allowedCharacters.contains(String.valueOf(character))) {
                return false;
            }
        }
        return true;
    }

    public boolean removeNickname(Player player) {
        Optional<PlayerIdentity> playerIdentity = nicknameService.getCachedPlayerIdentity(player);
        if (playerIdentity.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("nickname.data_unavailable")
                    .forAudience(player)
                    .build());
            return false;
        }

        nicknameService.removeNickname(playerIdentity.get());
        nicknameCache.remove(player.getUniqueId());
        return true;
    }
}
