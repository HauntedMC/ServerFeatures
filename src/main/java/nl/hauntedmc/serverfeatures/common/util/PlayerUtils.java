package nl.hauntedmc.serverfeatures.common.util;

import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import org.bukkit.entity.Player;

public class PlayerUtils {

    /**
     * Resolve from the Language feature API if available (no DB hit here).
     * Falls back to NL to keep previous behavior if the feature is disabled.
     */
    public static Language getPlayerLanguage(Player player) {
        return APIRegistry.get(LanguageAPI.class)
                .map(api -> api.get(player.getUniqueId()))
                .orElse(Language.NL);
    }
}
