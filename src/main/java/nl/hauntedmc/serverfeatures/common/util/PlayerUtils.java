package nl.hauntedmc.serverfeatures.common.util;

import nl.hauntedmc.commonlib.localization.Language;
import org.bukkit.entity.Player;

public class PlayerUtils {

    /**
     * Default method for detecting the player's language.
     * Modify this as needed to reflect a player's actual language.
     */
    public static Language getPlayerLanguage(Player player) {
        return Language.NL;
    }


}
