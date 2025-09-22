package nl.hauntedmc.serverfeatures.features.glow;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.glow.command.GlowCommand;
import nl.hauntedmc.serverfeatures.features.glow.internal.GlowHandler;
import nl.hauntedmc.serverfeatures.features.glow.listener.GlowListener;
import nl.hauntedmc.serverfeatures.features.glow.meta.Meta;

/**
 * Main Glow feature class. Holds configuration, messages, and references
 * to the {@link GlowHandler}, which actually performs the glow logic.
 */
public class Glow extends BukkitBaseFeature<Meta> {

    private GlowHandler glowHandler;

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

        // Legacy/compat
        m.add("glow.invalid_color", "&cOngeldige kleur optie.");
        m.add("glow.usage", "&cGebruik: /glow of /glow remove");
        m.add("glow.glow_set", "&aJe hebt nu een &7{color} &aglow effect.");
        m.add("glow.glow_removed", "&7Glow effect is verwijderd.");
        m.add("glow.no_active_glow", "&7Je had geen glow actief.");
        m.add("glow.menu.title", "&eGlow Kleur Kiesmenu");
        m.add("glow.menu.color.name", "&f{color}");
        m.add("glow.menu.color.lore.allowed", "&7Klik om deze glow te activeren.");
        m.add("glow.menu.color.lore.locked", "&cJe hebt deze glow kleur nog niet unlocked.");
        m.add("glow.menu.remove.name", "&cVerwijder Glow");
        m.add("glow.menu.remove.lore", "&7Klik om je huidige glow uit te zetten.");
        m.add("glow.menu.close.name", "&7Sluiten");
        m.add("glow.menu.close.lore", "&7Klik om dit menu te sluiten.");
        m.add("glow.menu.status.active", "&aHuidige glow: &7{color}");
        m.add("glow.menu.status.inactive", "&7Geen glow actief.");
        m.add("glow.menu.status.lore", "&7Selecteer een kleur of verwijder je glow.");

        return m;
    }

    @Override
    public void initialize() {
        this.glowHandler = new GlowHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new GlowListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new GlowCommand(this));
    }

    @Override
    public void disable() {
        // No special logic required on disable for this feature.
    }

    /** Exposes the GlowHandler, so other classes (listener, commands) can perform glow operations. */
    public GlowHandler getGlowHandler() {
        return glowHandler;
    }
}
