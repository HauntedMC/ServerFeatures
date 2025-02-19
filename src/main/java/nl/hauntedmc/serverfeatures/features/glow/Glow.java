package nl.hauntedmc.serverfeatures.features.glow;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.command.GlowCommand;
import nl.hauntedmc.serverfeatures.features.glow.listener.GlowListener;
import nl.hauntedmc.serverfeatures.features.glow.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.entity.Player;

import java.util.*;

public class Glow extends BaseFeature<Meta> {

    public Glow(ServerFeatures plugin) {
        super(plugin, new Meta());
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
        getLifecycleManager().registerListener(new GlowListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new GlowCommand(this));
    }

    /**
     * Enables glow for a player using the provided color.
     *
     * @param player the player to glow
     * @param glowColor  the glow color
     */
    public void setGlow(Player player, NamedTextColor glowColor) {
        player.setGlowing(true);
        if (!ScoreboardManager.hasValidTeam(player)) {
            return;
        }
        ScoreboardManager.setTeamColor(player, glowColor);
    }

    /**
     * Disables the glow effect for a player.
     *
     * @param player the player to remove glow from
     */
    public void removeGlow(Player player) {
        player.setGlowing(false);
        if (!ScoreboardManager.hasValidTeam(player)) {
            return;
        }
        ScoreboardManager.setTeamColor(player, NamedTextColor.GRAY);
    }
}
