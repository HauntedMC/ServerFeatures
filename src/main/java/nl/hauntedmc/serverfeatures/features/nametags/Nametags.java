package nl.hauntedmc.serverfeatures.features.nametags;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.LuckPermsHook;
import nl.hauntedmc.serverfeatures.features.nametags.internal.hook.PlaceholderHook;
import nl.hauntedmc.serverfeatures.features.nametags.listener.NametagListener;
import nl.hauntedmc.serverfeatures.features.nametags.meta.Meta;

public class Nametags extends BukkitBaseFeature<Meta> {
    private NametagManager nametagManager;

    public Nametags(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        // Core toggles
        defaults.put("enabled", false);
        defaults.put("update_interval_ticks", 10);        // mount self-heal tick (default ~0.5s)
        defaults.put("y_offset_blocks", 1.80d);           // where to spawn before mounting (failsafe)
        defaults.put("translation_y", 0.30d);             // relative Y offset while mounted
        defaults.put("line_width", 200);
        defaults.put("shadow", true);
        defaults.put("see_through", true);
        defaults.put("use_default_bg", false);
        defaults.put("background_argb", 0x00000000);      // fully transparent
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nametags.prefix", "%vault_prefix%");
        messages.add("nametags.playername", "&7%player_name%");
        messages.add("nametags.suffix", "%vault_suffix%");
        return messages;
    }

    @Override
    public void initialize() {
        new PlaceholderHook(this);
        this.nametagManager = new NametagManager(this);
        this.nametagManager.initializeOnlinePlayers();
        getLifecycleManager().getListenerManager().registerListener(new NametagListener(this));
        LuckPermsHook.subscribeLuckPermsHook(this);
    }

    @Override
    public void disable() {
        if (nametagManager != null) {
            nametagManager.removeAllNametags();
            nametagManager = null;
        }
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }
}
