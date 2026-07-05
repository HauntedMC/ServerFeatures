package nl.hauntedmc.serverfeatures.features.nickname.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
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
        this.allowedCharacters = CastUtils.safeCastToList(feature.getConfigHandler().get("allowedCharacters"), String.class);
        this.disallowedFormatting = CastUtils.safeCastToList(feature.getConfigHandler().get("disallowedFormatting"), String.class);
    }

    private static @NotNull String translateColours(String unformattedNickname) {
        return TextFormatter.convert(unformattedNickname)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .toMiniMessage();
    }

    public Optional<String> getNickname(OfflinePlayer player) {
        UUID playerId = player.getUniqueId();

        String cachedNickname = nicknameCache.get(playerId);
        if (cachedNickname != null) {
            return Optional.of(cachedNickname);
        }

        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);
        if (playerEntityOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> databaseNickname = nicknameService.getNickname(playerEntityOpt.get());
        databaseNickname.ifPresent(nick -> nicknameCache.put(playerId, nick));
        return databaseNickname;
    }

    public void loadNicknameIntoCache(Player player) {
        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);
        playerEntityOpt.ifPresent(playerEntity -> nicknameService.getNickname(playerEntity)
                .ifPresentOrElse(
                        nick -> nicknameCache.put(player.getUniqueId(), nick),
                        () -> nicknameCache.remove(player.getUniqueId())
                ));
    }

    public boolean setNickname(Player player, String unformattedNickname) {
        for (String disallowed : disallowedFormatting) {
            if (unformattedNickname.contains(disallowed)) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.disallowed_formatting").forAudience(player).build());
                return false;
            }
        }
        String nickname = translateColours(unformattedNickname);

        String plainTextNickname = TextFormatter.toPlain(nickname);

        if (!hasValidNicknameLength(plainTextNickname)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.max_length_exceeded").forAudience(player).build());
            return false;
        }

        if (!hasValidNicknameCharacters(plainTextNickname)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.invalid_characters").forAudience(player).build());
            return false;
        }

        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);
        if (playerEntityOpt.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.data_unavailable").forAudience(player).build());
            return false;
        }

        PlayerEntity playerEntity = playerEntityOpt.get();
        nicknameService.setNickname(playerEntity, nickname);
        nicknameCache.put(player.getUniqueId(), nickname);
        return true;
    }

    private boolean hasValidNicknameLength(String plainTextNickname) {
        return plainTextNickname.length() <= this.maxNicknameLength && plainTextNickname.length() >= this.minNicknameLength;
    }

    private boolean hasValidNicknameCharacters(String plainTextNickname) {
        for (int i = 0; i < plainTextNickname.length(); i++) {
            String charAsString = String.valueOf(plainTextNickname.charAt(i));
            if (!Character.isLetterOrDigit(plainTextNickname.charAt(i)) && !allowedCharacters.contains(charAsString)) {
                return false;
            }
        }
        return true;
    }

    public boolean removeNickname(Player player) {
        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);
        if (playerEntityOpt.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.data_unavailable").forAudience(player).build());
            return false;
        }

        PlayerEntity playerEntity = playerEntityOpt.get();
        nicknameService.removeNickname(playerEntity);
        nicknameCache.remove(player.getUniqueId());
        return true;
    }

}
