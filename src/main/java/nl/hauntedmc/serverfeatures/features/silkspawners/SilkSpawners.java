package nl.hauntedmc.serverfeatures.features.silkspawners;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.silkspawners.command.SilkSpawnerCommand;
import nl.hauntedmc.serverfeatures.features.silkspawners.internal.SilkSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.silkspawners.listener.SilkSpawnersListener;
import nl.hauntedmc.serverfeatures.features.silkspawners.meta.Meta;

import java.util.List;

public class SilkSpawners extends BukkitBaseFeature<Meta> {

    private SilkSpawnersHandler handler;

    public SilkSpawners(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("allowed_spawner_types", List.of(
                "ZOMBIE",
                "SPIDER",
                "CAVE_SPIDER",
                "BLAZE",
                "SILVERFISH",
                "SKELETON",
                "MAGMA_CUBE"));
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap msgs = new MessageMap();
        msgs.add("silkspawners.no_space",
                "&7[&bSpawner&7] &cMaak ruimte in je inventaris om de spawner op te kunnen pakken.");
        msgs.add("silkspawners.success",
                "&7[&bSpawner&7] &7Je hebt een &e{type} &7spawner opgepakt.");
        msgs.add("silkspawners.not_allowed_type",
                "&7[&bSpawner&7] &cJe mag geen &7{type} &cspawner plaatsen of breken.");
        msgs.add("silkspawners.give_usage",
                "&cUsage: /silkspawners give <speler> <mobtype> <aantal>");
        msgs.add("silkspawners.player_not_found",
                "&cSpeler &6{player}&c is niet online.");
        msgs.add("silkspawners.invalid_mobtype",
                "&cOngeldig mob type: &6{type}");
        msgs.add("silkspawners.invalid_amount",
                "&cOngeldig aantal opgegeven.");
        msgs.add("silkspawners.give_success",
                "&7[&bSpawner&7] &7Je hebt &e{amount} &e{type} &7spawner(s) gegeven aan &6{player}&7.");
        msgs.add("silkspawners.receive_success",
                "&7[&bSpawner&7] &7Je hebt &e{amount} &e{type} &7spawner(s) gekregen.");
        return msgs;
    }

    @Override
    public void initialize() {
        this.handler = new SilkSpawnersHandler(this);
        getLifecycleManager()
                .getListenerManager()
                .registerListener(new SilkSpawnersListener(this));
        getLifecycleManager()
                .getCommandManager()
                .registerFeatureCommand(new SilkSpawnerCommand(this));
    }

    @Override
    public void disable() {
        // nothing special
    }

    public SilkSpawnersHandler getHandler() {
        return handler;
    }
}
