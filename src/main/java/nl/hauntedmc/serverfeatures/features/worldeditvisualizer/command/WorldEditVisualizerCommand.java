package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command;

import nl.hauntedmc.serverfeatures.commands.CommandSpec;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WorldEditVisualizerCommand extends FeatureCommand {

    private final WorldEditVisualizer feature;
    private final VisualizationService service;

    public WorldEditVisualizerCommand(WorldEditVisualizer feature, VisualizationService service) {
        super(new CommandSpec.Builder("worldeditvisualizer")
                .description("Toggle the WorldEdit selection visualizer")
                .usage("/wevis toggle")
                .aliases(List.of("wevis"))
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

        // Toggle and message based on resulting state (true = enabled, false = disabled)
        boolean nowEnabled = service.toggle(p);

        p.sendMessage(feature.getLocalizationHandler()
                .getMessage(nowEnabled ? "worldeditvisualizer.enabled" : "worldeditvisualizer.disabled")
                .forAudience(p).build());

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of("toggle");
    }
}
