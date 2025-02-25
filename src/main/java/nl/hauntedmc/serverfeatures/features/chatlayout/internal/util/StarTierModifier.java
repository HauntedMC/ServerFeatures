package nl.hauntedmc.serverfeatures.features.chatlayout.internal.util;

import org.bukkit.entity.Player;

public class StarTierModifier {

    public static int getStarTier(Player player) {
        if (player.hasPermission("chatformat.bypass")) return 0;
        else if (player.hasPermission("chatformat.d500")) return 9;
        else if (player.hasPermission("chatformat.d450")) return 8;
        else if (player.hasPermission("chatformat.d400")) return 7;
        else if (player.hasPermission("chatformat.d350")) return 6;
        else if (player.hasPermission("chatformat.d300")) return 5;
        else if (player.hasPermission("chatformat.d250")) return 4;
        else if (player.hasPermission("chatformat.d200")) return 3;
        else if (player.hasPermission("chatformat.d150")) return 2;
        else if (player.hasPermission("chatformat.d100")) return 1;
        else return 0; // Default tier
    }

    public static String getStarTierFormat(int tier) {
        return switch (tier) {
            case 1 -> "<gold>✯ ";
            case 2 -> "<gold>✯✯ ";
            case 3 -> "<gold>✯✯✯ ";
            case 4 -> "<white>✯<gold>✯✯ ";
            case 5 -> "<white>✯✯<gold>✯ ";
            case 6 -> "<white>✯✯✯ ";
            case 7 -> "<yellow>✯<white>✯✯ ";
            case 8 -> "<yellow>✯✯<white>✯ ";
            case 9 -> "<yellow>✯✯✯ ";
            default -> ""; // Default case for tier 0
        };
    }
}