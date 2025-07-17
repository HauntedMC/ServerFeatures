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

    private final GlowHandler glowHandler;

    public Glow(ServerFeatures plugin) {
        super(plugin, new Meta());
        // Initialize the handler with "this" so it can access feature methods.
        this.glowHandler = new GlowHandler(this);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("glow.invalid_color", "&cOngeldige kleur optie.");
        messages.add("glow.usage", "&cUsage: /glow <color|remove>");
        messages.add("glow.glow_set", "&aJe hebt nu een &7{color} &aglow effect.");
        messages.add("glow.glow_removed", "&7Glow effect is verwijderd.");
        return messages;
    }

    @Override
    public void initialize() {
        // Register listener and command
        getLifecycleManager().getListenerManager().registerListener(new GlowListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new GlowCommand(this));
    }

    @Override
    public void disable() {
        // Nothing specific to do on disable, but a placeholder is here for clarity.
    }

    /**
     * Exposes the GlowHandler, so other classes (listener, commands) can perform glow operations.
     */
    public GlowHandler getGlowHandler() {
        return glowHandler;
    }
}
