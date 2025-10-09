package nl.hauntedmc.serverfeatures.api.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaceholderAPIHook {

    public static String parseWithPAPI(String text, Player player) {
        String output = text;
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            output = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return output;
    }

}
