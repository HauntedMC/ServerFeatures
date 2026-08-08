package nl.hauntedmc.serverfeatures.features.builtincommandblocker;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal.BuiltinCommandBlockerService;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal.BuiltinCommandSource;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.listener.BuiltinCommandBlockerListener;
import nl.hauntedmc.serverfeatures.features.builtincommandblocker.meta.Meta;
import org.bukkit.event.HandlerList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuiltinCommandBlocker extends BukkitBaseFeature<Meta> {

    private BuiltinCommandBlockerService service;
    private BuiltinCommandBlockerListener listener;

    public BuiltinCommandBlocker(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        for (BuiltinCommandSource source : BuiltinCommandSource.values()) {
            defaults.put("block." + source.configKey(), true);
        }
        defaults.put("block.legacy_aliases", true);
        defaults.put("remove_from_command_map", false);
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
        service = new BuiltinCommandBlockerService(
                this,
                getPlugin().getServer().getCommandMap()
        );
        BuiltinCommandBlockerService initializedService = service;
        getConfigHandler().registerReloadListener(() -> scheduleRefresh(initializedService));
        listener = new BuiltinCommandBlockerListener(this, service);
        getLifecycleManager().getListenerManager().registerListener(listener);

        // During plugin startup Bukkit registers commands.yml aliases after plugins have enabled. Hard-removing their
        // targets here would make those aliases fail registration entirely. The first scheduler tick runs after the
        // startup command registry has settled; runtime feature enables likewise become effective on the next tick.
        scheduleRefresh(initializedService);
    }

    @Override
    public void disable() {
        if (listener != null) {
            // BukkitBaseFeature invokes disable() before lifecycle cleanup unregisters listeners. Detach this listener
            // first so the command-tree refresh below cannot immediately filter the restored commands again.
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (service != null) {
            service.restoreRemovedCommands();
            service.updatePlayers();
            service = null;
        }
    }

    private void scheduleRefresh(BuiltinCommandBlockerService targetService) {
        getLifecycleManager().getTaskManager().scheduleOneTimeTask(targetService::refreshAndUpdatePlayers);
    }

    private static Map<String, Integer> emptySourceCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (BuiltinCommandSource source : BuiltinCommandSource.values()) {
            counts.put(source.configKey(), 0);
        }
        counts.put("legacy_aliases", 0);
        return counts;
    }
}
