package nl.hauntedmc.serverfeatures.features.glow;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.glow.command.GlowCommand;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowRegistry;
import nl.hauntedmc.serverfeatures.features.glow.internal.GlowHandler;
import nl.hauntedmc.serverfeatures.features.glow.listener.GlowListener;
import nl.hauntedmc.serverfeatures.features.glow.meta.Meta;

/**
 * Main Glow feature class. Holds configuration, messages, and references
 * to the {@link GlowHandler}, which performs the glow logic and ticking.
 */
public class Glow extends BukkitBaseFeature<Meta> {

    private GlowHandler glowHandler;
    private GlowRegistry registry;

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

        m.add("glow.invalid_color", "&cOngeldige kleur optie.");
        m.add("glow.usage", "&cGebruik: /glow of /glow remove");
        m.add("glow.glow_set", "&aJe hebt nu een &7{color} &aglow effect.");
        m.add("glow.glow_removed", "&7Glow effect is verwijderd.");
        m.add("glow.no_active_glow", "&7Je had geen glow actief.");
        m.add("glow.menu.title", "&8&lGlow Effect Menu");
        m.add("glow.menu.color.name", "&eGlow Effect: &f{color}");
        m.add("glow.menu.color.lore.allowed", "&aKlik om deze glow te activeren.");
        m.add("glow.menu.color.lore.locked", "&cJe hebt deze glow kleur nog niet unlocked.");
        m.add("glow.menu.remove.name", "&ct&lVerwijder Glow");
        m.add("glow.menu.remove.lore", "&7Klik om je huidige glow uit te zetten.");
        m.add("glow.menu.close.name", "&c&lSluiten");
        m.add("glow.menu.close.lore", "&7Klik om dit menu te sluiten.");
        m.add("glow.menu.status.active", "&6&lStatus: &r&f{color} &7Glow Effect actief.");
        m.add("glow.menu.status.inactive", "&6&lStatus: &r&7Geen glow actief.");
        m.add("glow.menu.status.lore", "&7Selecteer een kleur of verwijder je glow.");

        return m;
    }

    @Override
    public void initialize() {
        this.registry = new GlowRegistry();
        this.glowHandler = new GlowHandler(this, registry);

        getLifecycleManager().getListenerManager().registerListener(new GlowListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new GlowCommand(this));
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
}
