package nl.hauntedmc.serverfeatures.features.builtincommandblocker;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal.BuiltinCommandBlockerService;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.listener.BuiltinCommandBlockerListener;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.meta.Meta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuiltinCommandBlocker extends BukkitBaseFeature<Meta> {

    private BuiltinCommandBlockerService service;

    public BuiltinCommandBlocker(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("block.minecraft", true);
        defaults.put("block.bukkit", true);
        defaults.put("block.paper", true);
        defaults.put("block.spigot", true);
        defaults.put("block.spark", true);
        defaults.put("block.legacy_aliases", true);
        defaults.put("allowed", List.of());
        defaults.put("generated.blocked_command_count", 0);
        defaults.put("generated.blocked_commands", List.of());
        defaults.put("generated.detected_sources", emptySourceCounts());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("builtincommandblocker.blocked", "<red>Dit commando is niet beschikbaar.</red>");
        return messages;
    }

    @Override
    public void initialize() {
        service = new BuiltinCommandBlockerService(this, getPlugin().getServer().getCommandMap());
        getLifecycleManager().getListenerManager().registerListener(new BuiltinCommandBlockerListener(this, service));
        service.refreshAndUpdatePlayers();
    }

    @Override
    public void disable() {
        service = null;
    }

    public BuiltinCommandBlockerService service() {
        return service;
    }

    private static Map<String, Integer> emptySourceCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put("minecraft", 0);
        counts.put("bukkit", 0);
        counts.put("paper", 0);
        counts.put("spigot", 0);
        counts.put("spark", 0);
        counts.put("legacy_aliases", 0);
        return counts;
    }
}
