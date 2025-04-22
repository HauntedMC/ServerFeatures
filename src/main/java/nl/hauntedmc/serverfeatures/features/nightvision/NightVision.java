package nl.hauntedmc.serverfeatures.features.nightvision;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.nightvision.command.NightVisionCommand;
import nl.hauntedmc.serverfeatures.features.nightvision.meta.Meta;

import java.util.HashMap;
import java.util.Map;

public class NightVision extends BukkitBaseFeature<Meta> {

    public NightVision(ServerFeatures plugin) {
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
        messages.add("nightvision.status", "&7NightVision is nu {status}&7.");
        return messages;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager().registerFeatureCommand(new NightVisionCommand(this));
    }

    @Override
    public void disable() {
    }
}
