package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WorldEditVizCommand extends FeatureCommand {

    private final WorldEditVisualizer feature;
    private final VisualizationService service;

    public WorldEditVizCommand(WorldEditVisualizer feature, VisualizationService service) {
        super(new CommandSpec.Builder("weviz")
                .description("Toggle the WorldEdit selection visualizer")
                .usage("/weviz toggle")
                .aliases(List.of("worldeditviz", "worldeditvisualizer"))
                .permission("serverfeatures.feature.worldeditvisualizer.use")
                .build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) return true;

        if (!p.hasPermission("serverfeatures.feature.worldeditvisualizer.use")) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(p).build());
            return true;
        }

        // Only toggle (as requested)
        boolean enabled = service.toggle(p);
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage(enabled ? "worldeditvisualizer.enabled" : "worldeditvisualizer.disabled")
                .forAudience(p).build());
        if (enabled) service.tryShowFromSelection(p, true);

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of("toggle");
    }
}
