package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.WorldEditVisualizer;
import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class WorldEditVisualizerCommand extends FeatureCommand {

    private static final String USE_PERMISSION = "serverfeatures.feature.worldeditvisualizer.use";

    private final WorldEditVisualizer feature;
    private final VisualizationService service;

    public WorldEditVisualizerCommand(WorldEditVisualizer feature, VisualizationService service) {
        super(new CommandMeta.Builder("worldeditvisualizer")
                .description("Toggle the WorldEdit selection visualizer")
                .usage("/wevis [toggle]")
                .aliases(List.of("wevis"))
                .permission(USE_PERMISSION)
                .build());
        this.feature = feature;
        this.service = service;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission").forAudience(player).build());
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("toggle"))) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("worldeditvisualizer.usage").forAudience(player).build());
            return true;
        }

        boolean nowEnabled = service.toggle(player);
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage(nowEnabled
                        ? "worldeditvisualizer.enabled"
                        : "worldeditvisualizer.disabled")
                .forAudience(player).build());
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String @NotNull [] args
    ) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return "toggle".startsWith(prefix) ? List.of("toggle") : List.of();
    }
}
