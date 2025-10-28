package nl.hauntedmc.serverfeatures.features.nametags;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.nametags.command.NametagCommand;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.LuckPermsHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import nl.hauntedmc.serverfeatures.features.nametags.listener.NametagListener;
import nl.hauntedmc.serverfeatures.features.nametags.meta.Meta;
import org.bukkit.Bukkit;

public class Nametags extends BukkitBaseFeature<Meta> {
    private NametagManager nametagManager;

    public Nametags(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("update_interval_ticks", 2);
        defaults.put("viewer_update_delay_ticks", 10);
        defaults.put("max_distance", 45);

        defaults.put("remount_fix.enabled", true);
        defaults.put("remount_fix.interval_ticks", 10);
        defaults.put("debounce_update_ticks", 5);

        return defaults;

    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nametags.prefix", "%vault_prefix%");
        messages.add("nametags.playername", "&7%player_name%");
        messages.add("nametags.suffix", "%vault_suffix%");

        messages.add("nametags.selfview.enabled", "&7Eigen nametag weergave is nu &aingeschakeld&7.");
        messages.add("nametags.selfview.disabled", "&7Eigen nametag weergave is nu &cuitgeschakeld&7.");
        messages.add("nametags.selfview.status_on", "&7Eigen nametag weergave is &aingeschakeld&7.");
        messages.add("nametags.selfview.status_off", "&7Eigen nametag weergave is &cuitgeschakeld&7.");
        messages.add("nametags.selfview.usage", "&7Gebruik: /nametag selfview on|off|toggle|status");
        return messages;
    }


    @Override
    public void initialize() {
        new PlaceholderHook(this);

        this.nametagManager = new NametagManager(this);
        this.nametagManager.initializeOnlinePlayers();

        getLifecycleManager().getListenerManager().registerListener(new NametagListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new NametagCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            LuckPermsHook.subscribeLuckPermsHook(this);
        }
    }

    @Override
    public void disable() {
        this.nametagManager.removeAllNametags();
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }
}