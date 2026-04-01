package nl.hauntedmc.serverfeatures.api.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.BiFunction;
import java.util.function.Predicate;

public class PlaceholderAPIHook {

    public static String applyPlaceholders(String text, Player player) {
        return applyPlaceholders(
                text,
                player,
                pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName),
                me.clip.placeholderapi.PlaceholderAPI::setPlaceholders
        );
    }

    static String applyPlaceholders(
            String text,
            Player player,
            Predicate<String> pluginEnabled,
            BiFunction<Player, String, String> resolver
    ) {
        String output = text;
        if (player != null && pluginEnabled.test("PlaceholderAPI")) {
            output = resolver.apply(player, text);
        }
        return output;
    }

}
