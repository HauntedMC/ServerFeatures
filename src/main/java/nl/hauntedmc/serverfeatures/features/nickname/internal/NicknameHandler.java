package nl.hauntedmc.serverfeatures.features.nickname.internal;

import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.common.util.ColorEncodingUtils;
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
    private final int maxNicknameLength;
    private final List<String> allowedCharacters;

    public NicknameHandler(Nickname feature) {
        this.feature = feature;
        this.nicknameService = new NicknameService(feature);
        this.maxNicknameLength = (int) feature.getConfigHandler().getSetting("maxNicknameLength");
        this.allowedCharacters = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("allowedCharacters"), String.class);
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
        String nickname = translateColours(unformattedNickname);

        if (!hasValidNicknameLength(nickname)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.max_length_exceeded").forAudience(player).build());
            return false;
        }

        if (!hasValidNicknameCharacters(nickname)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("nickname.invalid_characters").forAudience(player).build());
            return false;
        }

        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);

        playerEntityOpt.ifPresent(playerEntity -> {
            nicknameService.setNickname(playerEntity, nickname);
            nicknameCache.put(player.getUniqueId(), nickname);
        });
        return true;
    }

    private static @NotNull String translateColours(String unformattedNickname) {
        String nickname = ColorEncodingUtils.translateHexColors(unformattedNickname);
        nickname = ColorEncodingUtils.translateAmpersandColors(nickname);
        nickname = ColorEncodingUtils.translateMiniMessageColors(nickname);
        return nickname;
    }

    private boolean hasValidNicknameLength(String formattedNickname) {
        String stripped = formattedNickname.replaceAll("§.", "");
        return stripped.length() <= this.maxNicknameLength;
    }

    private boolean hasValidNicknameCharacters(String formattedNickname) {
        String stripped = formattedNickname.replaceAll("§.", "");

        for (int i = 0; i < stripped.length(); i++) {
            String charAsString = String.valueOf(stripped.charAt(i));
            if (!Character.isLetterOrDigit(stripped.charAt(i)) && !allowedCharacters.contains(charAsString)) {
                return false;
            }
        }
        return true;
    }

    public void removeNickname(Player player) {
        Optional<PlayerEntity> playerEntityOpt = nicknameService.getPlayerEntity(player);

        playerEntityOpt.ifPresent(playerEntity -> {
            nicknameService.removeNickname(playerEntity);
            nicknameCache.remove(player.getUniqueId());
        });
    }

}
