package nl.hauntedmc.serverfeatures.features.itemedit;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.itemedit.internal.ItemHandler;
import nl.hauntedmc.serverfeatures.features.itemedit.listener.AnvilListener;
import nl.hauntedmc.serverfeatures.features.itemedit.meta.Meta;

import java.util.List;

public class ItemEdit extends BukkitBaseFeature<Meta> {

    private ItemHandler itemHandler;

    public ItemEdit(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("blockedWords", List.of(
                        "kut",
                        "godverdomme"
                )
        );
        defaults.put("blockedAnvilItems", List.of("CHEST", "HOPPER"));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new AnvilListener(this));
        itemHandler = new ItemHandler(this);
    }

    @Override
    public void disable() {
    }

    public ItemHandler getItemHandler() {
        return itemHandler;
    }
}
