package nl.hauntedmc.serverfeatures.features.itemedit;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.itemedit.internal.ItemHandler;
import nl.hauntedmc.serverfeatures.features.itemedit.listener.AnvilListener;
import nl.hauntedmc.serverfeatures.features.itemedit.meta.Meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemEdit extends BukkitBaseFeature<Meta> {

    private ItemHandler itemHandler;

    public ItemEdit(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
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
