package nl.hauntedmc.serverfeatures.features.glow;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.command.GlowCommand;
import nl.hauntedmc.serverfeatures.features.glow.internal.GlowHandler;
import nl.hauntedmc.serverfeatures.features.glow.listener.GlowListener;
import nl.hauntedmc.serverfeatures.features.glow.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Main Glow feature class. Holds configuration, messages, and references
 * to the {@link GlowHandler}, which actually performs the glow logic.
 */
public class Glow extends BaseFeature<Meta> {

    private final GlowHandler glowHandler;

    public Glow(ServerFeatures plugin) {
        super(plugin, new Meta());
        // Initialize the handler with "this" so it can access feature methods.
        this.glowHandler = new GlowHandler(this);
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        // Using MiniMessage-style tags for modern formatting.
        messages.add("glow.invalid_color", "<red>Ongeldige kleur optie.");
        messages.add("glow.usage", "<yellow>Usage: /glow <color|remove>");
        messages.add("glow.glow_set", "<green>Je hebt nu een <gray>{color} <green>glow effect.");
        messages.add("glow.glow_removed", "<green>Glow effect is verwijderd.");
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
