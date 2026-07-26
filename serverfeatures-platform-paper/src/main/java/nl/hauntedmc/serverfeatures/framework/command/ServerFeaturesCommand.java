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
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureDescriptor;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureDataManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Brigadier version of /serverfeatures using Paper’s lifecycle registrar.
 */
public final class ServerFeaturesCommand {

    private static final String P_STATUS = "serverfeatures.command.status";
    private static final String P_LIST = "serverfeatures.command.list";
    private static final String P_RELOAD = "serverfeatures.command.reload";
    private static final String P_ENABLE = "serverfeatures.command.enable";
    private static final String P_DISABLE = "serverfeatures.command.disable";
    private static final String P_RELOADLOC = "serverfeatures.command.reloadlocal";
    private static final String P_INFO = "serverfeatures.command.info";

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("serverfeatures");

        // /serverfeatures status
        root.then(Commands.literal("status")
                .requires(src -> src.getSender().hasPermission(P_STATUS))
                .executes(ctx -> {
                    sendPluginStatus(ctx.getSource().getSender());
                    return 1;
                }));

        // /serverfeatures list  (compact one-liner) + flag "--version"
        root.then(Commands.literal("list")
                .requires(src -> src.getSender().hasPermission(P_LIST))
                .then(Commands.literal("--version")
                        .executes(ctx -> {
                            listLoadedFeaturesOneLine(ctx.getSource().getSender(), true);
                            return 1;
                        }))
                .executes(ctx -> {
                    listLoadedFeaturesOneLine(ctx.getSource().getSender(), false);
                    return 1;
                }));

        // /serverfeatures info <feature>
        root.then(Commands.literal("info")
                .requires(src -> src.getSender().hasPermission(P_INFO))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> {
                            suggestAnyFeature(b);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "feature");
                            handleInfo(ctx.getSource().getSender(), name);
                            return 1;
                        }))
        );

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
                        .suggests((c, b) -> {
                            suggestLoadedFeatures(b);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            handleSoftReload(ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "feature"));
                            return 1;
                        })));

        // /serverfeatures reload <feature>
        root.then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission(P_RELOAD))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> {
                            suggestLoadedFeatures(b);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            handleReload(ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "feature"));
                            return 1;
                        })));

        // /serverfeatures disable <feature>
        root.then(Commands.literal("disable")
                .requires(src -> src.getSender().hasPermission(P_DISABLE))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> {
                            suggestLoadedFeatures(b);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            handleDisable(ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "feature"));
                            return 1;
                        })));

        // /serverfeatures enable <feature>
        root.then(Commands.literal("enable")
                .requires(src -> src.getSender().hasPermission(P_ENABLE))
                .then(Commands.argument("feature", StringArgumentType.word())
                        .suggests((c, b) -> {
                            suggestEnableCandidates(b);
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            handleEnable(ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "feature"));
                            return 1;
                        })));

        return root.build();
    }

    /* ==== suggestions with tooltips ==== */

    private void suggestLoadedFeatures(SuggestionsBuilder b) {
        final String prefix = b.getRemainingLowerCase();
        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();
        for (String featureKey : reg.getLoadedFeatureNames()) {
            if (featureKey == null) continue;
            String low = featureKey.toLowerCase(Locale.ROOT);
            if (!low.startsWith(prefix)) continue;

            FeatureDescriptor descriptor = reg.getAvailableFeature(featureKey);
            String version = descriptor == null ? "?" : Objects.toString(descriptor.featureVersion(), "?");
            String displayName = descriptor == null ? featureKey : Objects.toString(descriptor.featureName(), featureKey);
            b.suggest(featureKey, MessageComponentSerializer.message()
                    .serialize(Component.text(displayName + " • v" + version).color(NamedTextColor.GRAY)));
        }
    }

    /**
     * Suggest any feature (enabled or disabled), with a tooltip showing status.
     */
    private void suggestAnyFeature(SuggestionsBuilder b) {
        final String prefix = b.getRemainingLowerCase();

        Set<String> suggested = new HashSet<>();
        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();
        for (String featureKey : reg.getLoadedFeatureNames()) {
            if (featureKey == null) continue;

            String low = featureKey.toLowerCase(Locale.ROOT);
            if (!low.startsWith(prefix)) continue;

            FeatureDescriptor descriptor = reg.getAvailableFeature(featureKey);
            String version = descriptor == null ? "?" : Objects.toString(descriptor.featureVersion(), "?");
            String displayName = descriptor == null ? featureKey : Objects.toString(descriptor.featureName(), featureKey);

            suggested.add(low);
            b.suggest(featureKey, MessageComponentSerializer.message()
                    .serialize(Component.text("enabled • " + displayName + " • v" + version).color(NamedTextColor.GREEN)));
        }

        Map<String, FeatureDescriptor> available = reg.getAvailableFeatures();
        for (Map.Entry<String, FeatureDescriptor> entry : available.entrySet()) {
            String name = entry.getKey();
            if (name == null) continue;
            String low = name.toLowerCase(Locale.ROOT);
            if (!low.startsWith(prefix)) continue;
            if (suggested.contains(low)) continue;

            FeatureDescriptor descriptor = entry.getValue();
            String version = descriptor == null ? "?" : Objects.toString(descriptor.featureVersion(), "?");
            String displayName = descriptor == null ? name : Objects.toString(descriptor.featureName(), name);
            b.suggest(name, MessageComponentSerializer.message()
                    .serialize(Component.text("disabled • " + displayName + " • v" + version).color(NamedTextColor.RED)));
        }
    }

    private void suggestEnableCandidates(SuggestionsBuilder b) {
        final String prefix = b.getRemainingLowerCase();
        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();
        Set<String> available = reg.getAvailableFeatures().keySet();

        Set<String> loadedLower = reg.getLoadedFeatureNames().stream()
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

    /* ==== handlers ==== */

    /**
     * New: /serverfeatures info <feature> (registry lookups only)
     */
    private void handleInfo(CommandSender sender, String featureName) {
        if (featureName == null || featureName.isBlank()) {
            sender.sendMessage(Component.text("Please provide a feature name.", NamedTextColor.RED));
            return;
        }

        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();

        // Try direct loaded-lookup
        BukkitBaseFeature<?> loaded = reg.getLoadedFeature(featureName);

        // Case-insensitive fallback among loaded
        if (loaded == null) {
            loaded = reg.getLoadedFeatures().stream()
                    .filter(f -> featureName.equalsIgnoreCase(f.getFeatureName()))
                    .findFirst().orElse(null);
        }

        if (loaded != null) {
            // Enabled: full details
            String name = Objects.toString(loaded.getFeatureName(), featureName);
            String version = Objects.toString(loaded.getFeatureVersion(), "?");
            List<String> pluginDeps = Optional.ofNullable(loaded.getPluginDependencies()).orElseGet(List::of);
            List<String> featureDeps = Optional.ofNullable(loaded.getDependencies()).orElseGet(List::of);

            sendFeatureInfo(sender, name, true, version, pluginDeps, featureDeps);
            return;
        }

        // Not loaded: resolve against available feature keys (case-insensitive, aliases)
        String availableKey = plugin.getFeatureLoadManager().resolveFeatureKey(featureName);

        if (availableKey != null) {
            FeatureDescriptor descriptor = reg.getAvailableFeature(availableKey);
            if (descriptor == null) {
                sendFeatureInfo(sender, availableKey, false, "?", List.of(), List.of());
                return;
            }

            sendFeatureInfo(
                    sender,
                    descriptor.featureName(),
                    false,
                    descriptor.featureVersion(),
                    List.copyOf(descriptor.pluginDependencies()),
                    List.copyOf(descriptor.featureDependencies())
            );
        } else {
            sender.sendMessage(Component.text("Feature not found: ", NamedTextColor.RED)
                    .append(Component.text(featureName, NamedTextColor.WHITE)));
        }
    }

    private void sendFeatureInfo(CommandSender sender,
                                 String name,
                                 boolean enabled,
                                 String version,
                                 List<String> pluginDeps,
                                 List<String> featureDeps) {
        sender.sendMessage(ServerFeaturesCommandView.buildFeatureInfoMessage(
                name,
                enabled,
                version,
                pluginDeps,
                featureDeps
        ));
    }

    /**
     * Renders a CSV list with colored items and differently colored commas. Handles empty.
     */
    private Component renderCsvColored(List<String> items, NamedTextColor itemColor, NamedTextColor commaColor, boolean showNone) {
        return ServerFeaturesCommandView.renderCsvColored(items, itemColor, commaColor, showNone);
    }

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

    /* ==== compact list rendering (alphabetized) ==== */

    private void listLoadedFeaturesOneLine(CommandSender sender, boolean withVersion) {
        List<BukkitBaseFeature<?>> loaded = new ArrayList<>(plugin.getFeatureLoadManager()
                .getFeatureRegistry().getLoadedFeatures());

        // Alphabetize by feature name (case-insensitive, null-safe)
        loaded.sort(Comparator.comparing(
                f -> Optional.ofNullable(f.getFeatureName()).orElse(""),
                String.CASE_INSENSITIVE_ORDER
        ));

        List<ServerFeaturesCommandView.FeatureListEntry> entries = loaded.stream()
                .map(f -> new ServerFeaturesCommandView.FeatureListEntry(
                        Objects.toString(f.getFeatureName(), "?"),
                        Objects.toString(f.getFeatureVersion(), "?")
                ))
                .toList();

        sender.sendMessage(ServerFeaturesCommandView.buildLoadedFeaturesOneLine(entries, withVersion));
    }

    private void sendPluginStatus(@NotNull CommandSender sender) {
        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();
        List<String> cmds = new ArrayList<>();
        int loadedCount = reg.getLoadedFeatureNames().size();
        int tasks = 0, listeners = 0, commands = 0, conns = 0;
        boolean dataProviderEnabled = plugin.getServer().getPluginManager().isPluginEnabled(BaseMeta.DATA_PROVIDER);
        boolean hasInitializedDataManager = false;

        for (String featureKey : reg.getLoadedFeatureNames()) {
            BukkitBaseFeature<?> f = reg.getLoadedFeature(featureKey);
            if (f == null) {
                continue;
            }

            commands += f.getLifecycleManager().getCommandManager().getTotalRegisteredCommandCount();
            cmds.addAll(f.getLifecycleManager().getCommandManager().getAllRegisteredCommandNames());
            tasks += f.getLifecycleManager().getTaskManager().getActiveTaskCount();
            listeners += f.getLifecycleManager().getListenerManager().getRegisteredListenerCount();

            if (dataProviderEnabled) {
                FeatureDescriptor descriptor = reg.getAvailableFeature(featureKey);
                if (descriptor == null || !descriptor.pluginDependencies().contains(BaseMeta.DATA_PROVIDER)) {
                    continue;
                }
                FeatureDataManager dataManager = f.getLifecycleManager().getDataManager();
                if (dataManager.isInitialized()) {
                    conns += dataManager.getActiveConnCount();
                    hasInitializedDataManager = true;
                }
            }
        }

        sender.sendMessage(Component.text("ServerFeatures Status:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Number of loaded features: " + loadedCount, NamedTextColor.WHITE));
        if (hasInitializedDataManager) {
            sender.sendMessage(Component.text("- Number of active database connections: " + conns, NamedTextColor.WHITE));
        }
        sender.sendMessage(Component.text("- Number of active tasks: " + tasks, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered listeners: " + listeners, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered commands: " + commands, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Registered commands: " + cmds, NamedTextColor.WHITE));
    }
}
