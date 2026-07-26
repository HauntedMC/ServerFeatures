package nl.hauntedmc.serverfeatures.features.balloons;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.balloons.command.BalloonsCommand;
import nl.hauntedmc.serverfeatures.features.balloons.internal.BalloonsHandler;
import nl.hauntedmc.serverfeatures.features.balloons.listener.BalloonsListener;
import nl.hauntedmc.serverfeatures.features.balloons.meta.Meta;
import nl.hauntedmc.serverfeatures.features.balloons.registry.BalloonRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Balloons feature, implemented in the ServerFeatures framework.
 */
public class Balloons extends BukkitBaseFeature<Meta> {

    private BalloonsHandler handler;
    private BalloonRegistry registry;

    public Balloons(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        return cfg;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("balloons.menu.title", "&8&lBallonnen");
        m.add("balloons.menu.balloon.name", "&eBallon: &f{name}");
        m.add("balloons.menu.balloon.lore.allowed", "&aKlik om deze ballon te activeren.");
        m.add("balloons.menu.balloon.lore.locked", "&cJe hebt deze ballon nog niet unlocked.");
        m.add("balloons.menu.remove.name", "&c&lVerwijder Ballon");
        m.add("balloons.menu.remove.lore", "&7Klik om je huidige ballon uit te zetten.");
        m.add("balloons.menu.close.name", "&c&lMenu Sluiten");
        m.add("balloons.menu.close.lore", "&7Klik om dit menu te sluiten.");
        m.add("balloons.menu.status.active", "&6&lHuidige ballon: &r&f{name}");
        m.add("balloons.menu.status.inactive", "&6&lHuidige ballon: &r&7Geen.");
        m.add("balloons.menu.status.lore", "&7Selecteer een ballon of verwijder je ballon.");
        m.add("balloons.menu.prev", "&7« &eVorige");
        m.add("balloons.menu.next", "&eVolgende &7»");
        m.add("balloons.removed", "&7Ballon is verwijderd.");
        m.add("balloons.no_active", "&7Je hebt geen ballon actief.");
        m.add("balloons.set", "&aJe ballon is geactiveerd: &f{name}");
        m.add("balloons.cannot_open_vehicle", "&bJe kunt het ballonmenu niet openen in een voertuig.");
        return m;
    }

    @Override
    public void initialize() {
        this.registry = new BalloonRegistry(this);
        this.registry.reloadFromConfig();
        this.handler = new BalloonsHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new BalloonsListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new BalloonsCommand(this));
    }

    @Override
    public void disable() {
        if (handler != null) handler.shutdown();
    }

    public BalloonsHandler getHandler() {
        return handler;
    }

    public BalloonRegistry getRegistry() {
        return registry;
    }
}
