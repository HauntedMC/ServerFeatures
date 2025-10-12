package nl.hauntedmc.serverfeatures.features.tablist;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.tablist.internal.TablistHandler;
import nl.hauntedmc.serverfeatures.features.tablist.listener.TablistListener;
import nl.hauntedmc.serverfeatures.features.tablist.meta.Meta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class Tablist extends BukkitBaseFeature<Meta> {

    private TablistHandler handler;

    public Tablist(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Returns default config values for the Tablist feature.
     */
    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("refresh_interval", 5);
        defaults.put("rank_order", List.of(
                "owner",
                "admin",
                "serveraccount",
                "modt3",
                "modt2",
                "modt1",
                "bouwteamt3",
                "bouwteamt2",
                "bouwteamt1",
                "eventteamt3",
                "eventteamt2",
                "eventteamt1",
                "mediateam",
                "discordteam",
                "ambassador",
                "streamer",
                "supremeplus",
                "supreme",
                "god",
                "legend",
                "elite",
                "premium",
                "speler",
                "default"
        ));

        return defaults;
    }

    /**
     * Returns the default messages for the Tablist feature.
     * Add any new placeholders you may need.
     */
    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("tablist.header",
                "&8&l[&b&lHaunted&6&lMC&8&l]\n" +
                        "&7www.hauntedmc.nl &f&l| &7%server_time_HH:mm dd-MM-yyyy%");
        messages.add("tablist.footer",
                "&b\uD83E\uDDED &7%server_name% (%vanish_playercount%/%server_max_players%) &f&l| &b\uD83D\uDCF6 &7%player_ping%ms &f&l| &b\uD83D\uDC8E &7store.hauntedmc.nl");

        messages.add("tablist.prefix", "%essentials_afk%%vault_prefix%");
        messages.add("tablist.playername", "&7%player_name%");
        messages.add("tablist.suffix", " &f%voicechat_installed%");

        return messages;
    }

    /**
     * Initialize the feature: create the handler, register listeners, and schedule refresh tasks.
     */
    @Override
    public void initialize() {
        this.handler = new TablistHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new TablistListener(this));

        long refreshInterval = (int) getConfigHandler().getSetting("refresh_interval") * 20L;

        getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(
                () -> handler.refreshAllPlayers(),
                BukkitTime.ticks(0L),
                BukkitTime.ticks(refreshInterval)
        );
    }

    /**
     * Disable the feature: clear tablist header/footer (optional).
     */
    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            handler.clearTablist(player);
        }
    }

    /**
     * Expose the handler for external use if needed (e.g., other classes).
     */
    public TablistHandler getHandler() {
        return handler;
    }
}
