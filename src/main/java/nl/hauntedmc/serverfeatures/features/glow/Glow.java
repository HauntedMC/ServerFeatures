package nl.hauntedmc.serverfeatures.features.glow;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowRegistry;
import nl.hauntedmc.serverfeatures.features.glow.entity.PlayerGlowStateEntity;
import nl.hauntedmc.serverfeatures.features.glow.internal.GlowHandler;
import nl.hauntedmc.serverfeatures.features.glow.listener.GlowListener;
import nl.hauntedmc.serverfeatures.features.glow.meta.Meta;
import nl.hauntedmc.serverfeatures.features.glow.service.GlowStateService;

/**
 * Main Glow feature class. Holds configuration, messages, and references
 * to the {@link GlowHandler}, which performs the glow logic and ticking.
 */
public class Glow extends BukkitBaseFeature<Meta> {

    private GlowHandler glowHandler;
    private GlowRegistry registry;

    // ORM / persistence
    private ORMContext ormContext;
    private GlowStateService glowStateService;

    public Glow(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        m.add("glow.usage", "&cGebruik: /glow of /glow remove");
        m.add("glow.glow_set", "&aJe hebt nu een &7{color} &aglow effect.");
        m.add("glow.glow_removed", "&7Glow effect is verwijderd.");
        m.add("glow.no_active_glow", "&7Je hebt geen glow effect op dit moment.");
        m.add("glow.menu.title", "&8&lGlow Effect Menu");
        m.add("glow.menu.color.name", "&eGlow Effect: &f{color}");
        m.add("glow.menu.color.lore.allowed", "&aKlik om deze glow effect te activeren.");
        m.add("glow.menu.color.lore.locked", "&cJe hebt deze glow effect nog niet unlocked.");
        m.add("glow.menu.remove.name", "&c&lVerwijder Glow");
        m.add("glow.menu.remove.lore", "&7Klik om je huidige glow effect uit te zetten.");
        m.add("glow.menu.close.name", "&c&lMenu Sluiten");
        m.add("glow.menu.close.lore", "&7Klik om dit menu te sluiten.");
        m.add("glow.menu.status.active", "&6&lHuidig effect: &r&f{color}");
        m.add("glow.menu.status.inactive", "&6&lHuidig effect: &r&7Geen.");
        m.add("glow.menu.status.lore", "&7Selecteer een kleur of verwijder je glow effect.");

        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("glowOrmConnection", DatabaseType.MYSQL, "player_data_rw");

        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "glowOrmConnection",
                PlayerEntity.class,
                PlayerGlowStateEntity.class
        ).orElseThrow();

        this.registry = new GlowRegistry();
        this.glowHandler = new GlowHandler(this);
        this.glowStateService = new GlowStateService(this);

        getLifecycleManager().getListenerManager().registerListener(new GlowListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new nl.hauntedmc.serverfeatures.features.glow.command.GlowCommand(this));
    }

    @Override
    public void disable() {
        // No special logic required on disable for this feature.
    }

    public GlowHandler getGlowHandler() {
        return glowHandler;
    }

    public GlowRegistry getGlowRegistry() {
        return registry;
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public GlowStateService getGlowStateService() {
        return glowStateService;
    }
}
