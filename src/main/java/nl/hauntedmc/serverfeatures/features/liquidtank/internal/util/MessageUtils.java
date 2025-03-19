package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.entity.Player;

public class MessageUtils {
    public static void sendTitle(Player paramPlayer, String paramString) {
        paramPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, (new ComponentBuilder(paramString
                .replace("&", "§"))).create());
    }
}
