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

    private static final List<String> ACTIONS = List.of("toggle", "on", "off", "refresh");

    private final WorldEditVisualizer feature;
    private final VisualizationService service;

    public WorldEditVisualizerCommand(WorldEditVisualizer feature, VisualizationService service) {
        super(new CommandMeta.Builder("worldeditvisualizer")
                .description("Toggle the packet-only WorldEdit selection visualizer")
                .usage("/wevis [toggle|on|off|refresh]")
                .aliases(List.of("wevis"))
                .permission(VisualizationService.USE_PERMISSION)
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
        if (!player.hasPermission(VisualizationService.USE_PERMISSION)) {
            send(player, "general.no_permission");
            return true;
        }

        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "toggle" -> toggle(player);
            case "on", "enable" -> enable(player);
            case "off", "disable" -> disable(player);
            case "refresh" -> refresh(player);
            default -> send(player, "worldeditvisualizer.usage");
        }
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
        return ACTIONS.stream().filter(action -> action.startsWith(prefix)).toList();
    }

    private void toggle(Player player) {
        VisualizationService.ToggleResult result = service.toggle(player);
        send(player, result.enabled()
                ? "worldeditvisualizer.enabled"
                : "worldeditvisualizer.disabled");
        if (result.enabled() && result.refreshResult() != VisualizationService.RefreshResult.RENDERED) {
            send(player, result.refreshResult().messageKey());
        }
    }

    private void enable(Player player) {
        VisualizationService.RefreshResult result = service.enable(player);
        send(player, "worldeditvisualizer.enabled");
        if (result != VisualizationService.RefreshResult.RENDERED) {
            send(player, result.messageKey());
        }
    }

    private void disable(Player player) {
        service.disable(player, true);
        send(player, "worldeditvisualizer.disabled");
    }

    private void refresh(Player player) {
        VisualizationService.RefreshResult result;
        if (service.isEnabled(player)) {
            result = service.refreshNow(player);
        } else {
            result = service.enable(player);
            send(player, "worldeditvisualizer.enabled");
        }
        send(player, result.messageKey());
    }

    private void send(Player player, String key) {
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
    }
}
