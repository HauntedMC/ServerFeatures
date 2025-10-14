package nl.hauntedmc.serverfeatures.framework.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/** Brigadier version of /serverfeatures using Paper’s lifecycle registrar. */
public final class ServerFeaturesCommand {

    private static final String P_STATUS     = "serverfeatures.command.status";
    private static final String P_LIST       = "serverfeatures.command.list";
    private static final String P_RELOAD     = "serverfeatures.command.reload";
    private static final String P_ENABLE     = "serverfeatures.command.enable";
    private static final String P_DISABLE    = "serverfeatures.command.disable";
    private static final String P_RELOADLOC  = "serverfeatures.command.reloadlocal";

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("serverfeatures");

        // /serverfeatures status
        root.then(Commands.literal("status")
                .requires(src -> src.getSender().hasPermission(P_STATUS))
                .executes(ctx -> { sendPluginStatus(ctx.getSource().getSender()); return 1; }));

        // /serverfeatures list
        root.then(Commands.literal("list")
                .requires(src -> src.getSender().hasPermission(P_LIST))
                .executes(ctx -> { listLoadedFeatures(ctx.getSource().getSender()); return 1; }));

        // /serverfeatures reloadlocal
        root.then(Commands.literal("reloadlocal")
                .requires(src -> src.getSender().hasPermission(P_RELOADLOC))
                .executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    try {
                        plugin.getLocalizationHandler().reloadLocalization();
                        s.sendMessage(plugin.getLocalizationHandler()
                                .getMessage("command.reloadlocal.success")
                                .forAudience(s).build());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Localization reload failed: " + t.getMessage());
                        s.sendMessage(plugin.getLocalizationHandler()
                                .getMessage("command.reloadlocal.fail")
                                .forAudience(s).build());
                    }
                    return 1;
                }));

        // /serverfeatures softreload <feature>
        root.then(Commands.literal("softreload")
                .requires(src -> src.getSender().hasPermission(P_RELOAD))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> { suggestLoadedFeatures(b); return b.buildFuture(); })
                        .executes(ctx -> { handleSoftReload(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "feature")); return 1; })));

        // /serverfeatures reload <feature>
        root.then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission(P_RELOAD))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> { suggestLoadedFeatures(b); return b.buildFuture(); })
                        .executes(ctx -> { handleReload(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "feature")); return 1; })));

        // /serverfeatures disable <feature>
        root.then(Commands.literal("disable")
                .requires(src -> src.getSender().hasPermission(P_DISABLE))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> { suggestLoadedFeatures(b); return b.buildFuture(); })
                        .executes(ctx -> { handleDisable(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "feature")); return 1; })));

        // /serverfeatures enable <feature>
        root.then(Commands.literal("enable")
                .requires(src -> src.getSender().hasPermission(P_ENABLE))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> { suggestEnableCandidates(b); return b.buildFuture(); })
                        .executes(ctx -> { handleEnable(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "feature")); return 1; })));

        return root.build();
    }

    /* ==== suggestions with tooltips ==== */

    private void suggestLoadedFeatures(SuggestionsBuilder b) {
        final String prefix = b.getRemainingLowerCase();
        var loaded = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        for (BukkitBaseFeature<?> f : loaded) {
            String name = f.getFeatureName();
            if (name == null) continue;
            String low = name.toLowerCase(Locale.ROOT);
            if (!low.startsWith(prefix)) continue;

            String version = Objects.toString(f.getFeatureVersion(), "?");
            b.suggest(name, MessageComponentSerializer.message()
                    .serialize(Component.text("v" + version).color(NamedTextColor.GRAY)));        }
    }

    private void suggestEnableCandidates(SuggestionsBuilder b) {
        final String prefix = b.getRemainingLowerCase();
        Set<String> available = plugin.getFeatureLoadManager()
                .getFeatureRegistry().getAvailableFeatures().keySet();

        Set<String> loadedLower = plugin.getFeatureLoadManager()
                .getFeatureRegistry().getLoadedFeatures().stream()
                .map(BukkitBaseFeature::getFeatureName)
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (String name : available) {
            if (name == null) continue;
            String low = name.toLowerCase(Locale.ROOT);
            if (loadedLower.contains(low)) continue;
            if (!low.startsWith(prefix)) continue;
            b.suggest(name);
        }
    }

    /* ==== handlers: same behavior as your Bukkit version ==== */

    private void handleEnable(CommandSender sender, String feature) {
        var resp = plugin.getFeatureLoadManager().enableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.success").forAudience(sender)
                    .with("feature", feature).build());
            case NOT_FOUND -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.not_found").forAudience(sender)
                    .with("feature", feature).build());
            case ALREADY_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.already_loaded").forAudience(sender)
                    .with("feature", feature).build());
            case MISSING_PLUGIN_DEPENDENCY -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.missing_plugin_dependency").forAudience(sender)
                    .with("feature", feature).with("plugins", String.join(", ", resp.missingPlugins())).build());
            case MISSING_FEATURE_DEPENDENCY -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.missing_feature_dependency").forAudience(sender)
                    .with("feature", feature).with("features", String.join(", ", resp.missingFeatures())).build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.failed").forAudience(sender)
                    .with("feature", feature).build());
        }
    }

    private void handleDisable(CommandSender sender, String feature) {
        var resp = plugin.getFeatureLoadManager().disableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.alsoDisabledDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success_with_dependents").forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.alsoDisabledDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success").forAudience(sender)
                            .with("feature", feature).build());
                }
            }
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.not_loaded").forAudience(sender)
                    .with("feature", feature).build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.failed").forAudience(sender)
                    .with("feature", feature).build());
        }
    }

    private void handleSoftReload(CommandSender sender, String feature) {
        var resp = plugin.getFeatureLoadManager().softReloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.success").forAudience(sender)
                    .with("feature", feature).build());
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.not_loaded").forAudience(sender)
                    .with("feature", feature).build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.failed").forAudience(sender)
                    .with("feature", feature).build());
        }
    }

    private void handleReload(CommandSender sender, String feature) {
        var resp = plugin.getFeatureLoadManager().reloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.reloadedDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success_with_dependents").forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.reloadedDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success").forAudience(sender)
                            .with("feature", feature).build());
                }
            }
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.not_loaded").forAudience(sender)
                    .with("feature", feature).build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.failed").forAudience(sender)
                    .with("feature", feature).build());
        }
    }

    private void listLoadedFeatures(CommandSender sender) {
        List<BukkitBaseFeature<?>> loaded = plugin.getFeatureLoadManager()
                .getFeatureRegistry().getLoadedFeatures();

        if (loaded.isEmpty()) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.empty")
                    .forAudience(sender)
                    .build());
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler()
                .getMessage("command.list.header")
                .forAudience(sender)
                .build());

        for (BukkitBaseFeature<?> feature : loaded) {
            String name = Objects.toString(feature.getFeatureName(), "?");
            String version = Objects.toString(feature.getFeatureVersion(), "?");
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.entry")
                    .forAudience(sender)
                    .with("feature", name)
                    .with("version", version)
                    .build());
        }
    }

    private void sendPluginStatus(@NotNull CommandSender sender) {
        List<BukkitBaseFeature<?>> loaded = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        List<String> cmds = new ArrayList<>();
        int loadedCount = loaded.size();
        int tasks = 0, listeners = 0, commands = 0, conns = 0;

        for (BukkitBaseFeature<?> f : loaded) {
            commands += f.getLifecycleManager().getCommandManager().getTotalRegisteredCommandCount();
            cmds.addAll(f.getLifecycleManager().getCommandManager().getAllRegisteredCommandNames());
            tasks += f.getLifecycleManager().getTaskManager().getActiveTaskCount();
            listeners += f.getLifecycleManager().getListenerManager().getRegisteredListenerCount();
            conns += f.getLifecycleManager().getDataManager().getActiveConnCount();
        }

        sender.sendMessage(Component.text("ServerFeatures Status:", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Number of loaded features: " + loadedCount, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active database connections: " + conns, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active tasks: " + tasks, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered listeners: " + listeners, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered commands: " + commands, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Registered commands: " + cmds, net.kyori.adventure.text.format.NamedTextColor.WHITE));
    }

}
