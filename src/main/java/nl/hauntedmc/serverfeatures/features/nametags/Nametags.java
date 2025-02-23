package nl.hauntedmc.serverfeatures.features.nametags;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.listener.NametagListener;
import nl.hauntedmc.serverfeatures.features.nametags.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.*;

import static nl.hauntedmc.serverfeatures.features.nametags.internal.hook.LuckPermsHook.subscribeLuckPermsHook;

public class Nametags extends BaseFeature<Meta> {
    private NametagManager nametagManager;

    public Nametags(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        return defaults;

    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("nametag.updated", "§aNametag updated!");
        return messages;
    }


    @Override
    public void initialize() {
        this.nametagManager = new NametagManager(this);
        this.nametagManager.initializeOnlinePlayers();
        getLifecycleManager().registerListener(new NametagListener(this));
        subscribeLuckPermsHook(this);
    }

    @Override
    public void disable() {
        this.nametagManager.removeAllNametags();
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }
}