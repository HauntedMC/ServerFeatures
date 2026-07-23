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

    public CompletionStage<Optional<PlayerIdentity>> findPlayerIdentity(String identifier) {
        return nicknameService.findPlayerIdentity(identifier);
    }

    public CompletionStage<NicknameMutationResult> setNickname(PlayerIdentity identity, String unformattedNickname) {
        NicknameMutationResult validation = validateNickname(unformattedNickname);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }

        String nickname = validation.nickname();
        return nicknameService.setNickname(identity, nickname)
                .thenRun(() -> nicknameCache.put(identity.uuid(), nickname))
                .thenApply(completed -> validation);
    }

    public CompletionStage<Void> removeNickname(PlayerIdentity identity) {
        return nicknameService.removeNickname(identity)
                .thenRun(() -> nicknameCache.remove(identity.uuid()));
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

    /**
     * Compatibility entry point for live-player callers. New command code should use the asynchronous
     * identity-based overload so success is reported only after persistence completes.
     */
    public boolean setNickname(Player player, String unformattedNickname) {
        NicknameMutationResult validation = validateNickname(unformattedNickname);
        if (!validation.success()) {
            sendValidationFailure(player, validation.failure());
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

        PlayerIdentity identity = playerIdentity.get();
        UUID playerUuid = player.getUniqueId();
        String nickname = validation.nickname();
        nicknameService.setNickname(identity, nickname)
                .thenRun(() -> nicknameCache.put(playerUuid, nickname))
                .exceptionally(throwable -> {
                    feature.getLogger().warning("Could not save nickname for " + identity.uuid() + ": "
                            + rootMessage(throwable));
                    return null;
                });
        return true;
    }

    private NicknameMutationResult validateNickname(String unformattedNickname) {
        if (unformattedNickname == null || unformattedNickname.isBlank()) {
            return NicknameMutationResult.failure(NicknameFailure.INVALID_LENGTH);
        }
        for (String disallowed : disallowedFormatting) {
            if (unformattedNickname.contains(disallowed)) {
                return NicknameMutationResult.failure(NicknameFailure.DISALLOWED_FORMATTING);
            }
        }

        String nickname = translateColours(unformattedNickname);
        String plainTextNickname = TextFormatter.toPlain(nickname);
        if (!hasValidNicknameLength(plainTextNickname)) {
            return NicknameMutationResult.failure(NicknameFailure.INVALID_LENGTH);
        }
        if (!hasValidNicknameCharacters(plainTextNickname)) {
            return NicknameMutationResult.failure(NicknameFailure.INVALID_CHARACTERS);
        }
        return NicknameMutationResult.success(nickname);
    }

    public void sendValidationFailure(Player player, NicknameFailure failure) {
        String key = switch (failure) {
            case DISALLOWED_FORMATTING -> "nickname.disallowed_formatting";
            case INVALID_LENGTH -> "nickname.max_length_exceeded";
            case INVALID_CHARACTERS -> "nickname.invalid_characters";
        };
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
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

    /**
     * Compatibility entry point for live-player callers.
     */
    public boolean removeNickname(Player player) {
        Optional<PlayerIdentity> playerIdentity = nicknameService.getCachedPlayerIdentity(player);
        if (playerIdentity.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("nickname.data_unavailable")
                    .forAudience(player)
                    .build());
            return false;
        }

        PlayerIdentity identity = playerIdentity.get();
        UUID playerUuid = player.getUniqueId();
        nicknameService.removeNickname(identity)
                .thenRun(() -> nicknameCache.remove(playerUuid))
                .exceptionally(throwable -> {
                    feature.getLogger().warning("Could not remove nickname for " + identity.uuid() + ": "
                            + rootMessage(throwable));
                    return null;
                });
        return true;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public enum NicknameFailure {
        DISALLOWED_FORMATTING,
        INVALID_LENGTH,
        INVALID_CHARACTERS
    }

    public record NicknameMutationResult(boolean success, String nickname, NicknameFailure failure) {
        public static NicknameMutationResult success(String nickname) {
            return new NicknameMutationResult(true, nickname, null);
        }

        public static NicknameMutationResult failure(NicknameFailure failure) {
            return new NicknameMutationResult(false, null, failure);
        }
    }
}
