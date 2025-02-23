package nl.hauntedmc.serverfeatures.features.nametags.internal.hook;


import net.milkbowl.vault.chat.Chat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.Bukkit;

public class VaultHook {

    private static Chat chat = null;

    static {
        RegisteredServiceProvider<Chat> rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
            chat = rsp.getProvider();
        }
    }

    /**
     * Returns the player's prefix from Vault, or an empty string if none is found.
     */
    public static String getPlayerPrefix(Player player) {
        if (chat != null) {
            String prefix = chat.getPlayerPrefix(player);
            return prefix != null ? prefix : "";
        }
        return "";
    }
}
