package nl.hauntedmc.serverfeatures.features.teleportation;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.teleportation.command.RandomTpCommand;
import nl.hauntedmc.serverfeatures.features.teleportation.command.TpPosCommand;
import nl.hauntedmc.serverfeatures.features.teleportation.internal.TeleportState;
import nl.hauntedmc.serverfeatures.features.teleportation.meta.Meta;
import nl.hauntedmc.serverfeatures.features.teleportation.service.TeleportService;

public class Teleportation extends BukkitBaseFeature<Meta> {

    private final TeleportState state = new TeleportState(this);
    private TeleportService service; // shared instance

    public Teleportation(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);

        // Outer bounds = WorldBorder (see TeleportBounds).
        // These are INNER "no-teleport" bounds and default disabled (0..0).
        cfg.put("min_x", 0);
        cfg.put("max_x", 0);
        cfg.put("min_z", 0);
        cfg.put("max_z", 0);

        // Behavior
        cfg.put("respect_world_border", true); // WorldBorder as outer bounds
        cfg.put("disabled_blocks", java.util.List.of("LAVA", "WATER", "LILY_PAD", "CACTUS"));
        cfg.put("play_sounds", true);

        // Attempts & offsets
        cfg.put("randomtp.max_attempts", 250);
        cfg.put("randomtp.y_offset_after_highest", 4.0D);

        // Cooldowns (sec)
        cfg.put("cooldown_seconds.randomtp", 10);
        cfg.put("cooldown_seconds.tppos", 10);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // Usage
        m.add("teleportation.usage.randomtp", "&eGebruik: /randomtp");
        m.add("teleportation.usage.tppos", "&eGebruik: /tppos <x> <y> <z>");

        // Progress/info
        m.add("teleportation.working.randomtp", "&7Bezig met zoeken naar een veilige locatie...");
        m.add("teleportation.working.tppos", "&7Bezig met teleporteren naar opgegeven coördinaten...");

        // Success
        m.add("teleportation.success.randomtp", "&bJe bent naar een willekeurige plek geteleporteerd. &aGebruik eventueel onze Dynmap: &7www.hauntedmc.nl/dynmap");
        m.add("teleportation.success.tppos", "&bJe bent naar de gewenste locatie geteleporteerd. &aBekijk ook onze Dynmap: &7www.hauntedmc.nl/dynmap");

        // Errors/validatie
        m.add("teleportation.cooldown_active", "&cJe kunt dit nog niet doen. Wacht &e{seconds}s&c.");
        m.add("teleportation.player_only", "&cNiet bruikbaar in de console.");
        m.add("teleportation.no_permission", "&cJe mag dit commando niet uitvoeren.");
        m.add("teleportation.outside_worldborder", "&cDeze locatie ligt buiten de &eWorldBorder&c.");

        // Legacy, retained for compatibility (not used by new logic)
        m.add("teleportation.out_of_bounds", "&cJe kunt alleen teleporteren binnen: &bX &7tussen &c{min_x} &7en &c{max_x}&7, &bZ &7tussen &c{min_z} &7en &c{max_z}&7.");
        m.add("teleportation.randomtp.no_safe_found", "&cKon geen veilige plek vinden na &e{attempts} &cpogingen. Probeer het zo nog eens.");
        m.add("teleportation.coords.invalid", "&cOngeldige coördinaten. Gebruik gehele getallen voor X/Y/Z.");

        // /tppos safety
        m.add("teleportation.tppos.not_safe", "&cGeen veilige plek gevonden op of onder deze coördinaat. &7Pas je &eY &7of &eX/Z &7aan en probeer opnieuw.");

        return m;
    }

    @Override
    public void initialize() {
        this.service = new TeleportService(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new RandomTpCommand(this, service));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new TpPosCommand(this, service));
    }

    @Override
    public void disable() {
        state.clearAll();
    }

    public TeleportState getState() {
        return state;
    }

    public TeleportService getService() {
        return service;
    }
}
