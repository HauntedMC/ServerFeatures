package nl.hauntedmc.serverfeatures.features.durabilityalert;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.durabilityalert.internal.DurabilityAlertHandler;
import nl.hauntedmc.serverfeatures.features.durabilityalert.listener.DurabilityAlertListener;
import nl.hauntedmc.serverfeatures.features.durabilityalert.meta.Meta;

public class DurabilityAlert extends BukkitBaseFeature<Meta> {

    public DurabilityAlert(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("defaultvalue", 10);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("durabilityalert.no_durability", "&f{item} &eis stuk! &c&l:(");
        messages.add("durabilityalert.low_durability", "&c&lWAARSCHUWING &f{item} &eis bijna stuk!");
        messages.add("durabilityalert.durability_left", " &eNog &c{durability} &edamage over!");
        return messages;
    }

    @Override
    public void initialize() {
        DurabilityAlertHandler alertHandler = new DurabilityAlertHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new DurabilityAlertListener(alertHandler));
    }

    @Override
    public void disable() {
    }

}
