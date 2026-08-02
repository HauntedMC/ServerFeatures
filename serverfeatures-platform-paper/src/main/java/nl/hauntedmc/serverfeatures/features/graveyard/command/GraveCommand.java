package nl.hauntedmc.serverfeatures.features.graveyard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.graveyard.ClaimReason;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveClaimOutcome;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Player and staff command tree for grave discovery, tracking and recovery.
 */
public final class GraveCommand implements BrigadierCommand {
    private static final String BASE = "serverfeatures.feature.graveyard.use";
    private static final String P_LIST = "serverfeatures.feature.graveyard.command.list";
    private static final String P_INFO = "serverfeatures.feature.graveyard.command.info";
    private static final String P_LOCATE = "serverfeatures.feature.graveyard.command.locate";
    private static final String P_TRACK = "serverfeatures.feature.graveyard.command.track";
    private static final String P_REMOTE_CLAIM = "serverfeatures.feature.graveyard.command.remoteclaim";
    private static final String P_ADMIN = "serverfeatures.feature.graveyard.admin";
    private static final String P_TELEPORT = P_ADMIN + ".teleport";
    private static final String P_RELOCATE = P_ADMIN + ".relocate";
    private static final String P_DELIVER = P_ADMIN + ".deliver";
    private static final String P_EXPIRE = P_ADMIN + ".expire";
    private static final String P_RESTORE = P_ADMIN + ".restore";
    private static final String P_PURGE = P_ADMIN + ".purge";
    private static final String P_DIAGNOSTICS = P_ADMIN + ".diagnostics";

    private final Graveyard feature;
    private final GraveManager manager;

    public GraveCommand(Graveyard feature, GraveManager manager) {
        this.feature = feature;
        this.manager = manager;
    }

    @Override
    public @NotNull String name() {
        return "grave";
    }

    @Override
    public List<String> aliases() {
        return List.of("graves");
    }

    @Override
    public String description() {
        return "Find and recover virtual death graves.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(BASE)
                        || source.getSender().hasPermission(P_ADMIN))
                .executes(context -> showOwnGraves(context.getSource().getSender(), false));

        root.then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission(P_LIST))
                .executes(context -> showOwnGraves(context.getSource().getSender(), true)));

        root.then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission(P_INFO))
                .then(graveArgument(false).executes(context -> showInfo(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("locate")
                .requires(source -> source.getSender().hasPermission(P_LOCATE))
                .then(graveArgument(false).executes(context -> locate(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("track")
                .requires(source -> source.getSender().hasPermission(P_TRACK))
                .then(Commands.literal("off").executes(context -> stopTracking(
                        context.getSource().getSender()
                )))
                .then(graveArgument(false).executes(context -> track(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission(P_REMOTE_CLAIM))
                .then(graveArgument(false).executes(context -> claimRemote(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(adminTree());
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminTree() {
        return Commands.literal("admin")
                .requires(source -> source.getSender().hasPermission(P_ADMIN))
                .then(Commands.literal("diagnostics")
                        .requires(source -> source.getSender().hasPermission(P_DIAGNOSTICS))
                        .executes(context -> diagnostics(context.getSource().getSender())))
                .then(Commands.literal("info")
                        .then(graveArgument(true).executes(context -> showInfo(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("teleport")
                        .requires(source -> source.getSender().hasPermission(P_TELEPORT))
                        .then(graveArgument(true).executes(context -> teleport(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("relocate")
                        .requires(source -> source.getSender().hasPermission(P_RELOCATE))
                        .then(graveArgument(true).executes(context -> relocate(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("deliver")
                        .requires(source -> source.getSender().hasPermission(P_DELIVER))
                        .then(graveArgument(true).executes(context -> deliver(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("expire")
                        .requires(source -> source.getSender().hasPermission(P_EXPIRE))
                        .then(graveArgument(true).executes(context -> transition(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id"),
                                AdminAction.EXPIRE
                        ))))
                .then(Commands.literal("restore")
                        .requires(source -> source.getSender().hasPermission(P_RESTORE))
                        .then(graveArgument(true).executes(context -> transition(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id"),
                                AdminAction.RESTORE
                        ))))
                .then(Commands.literal("purge")
                        .requires(source -> source.getSender().hasPermission(P_PURGE))
                        .then(graveArgument(true)
                                .then(Commands.literal("confirm").executes(context -> transition(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "grave_id"),
                                        AdminAction.PURGE
                                )))));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> graveArgument(
            boolean allGraves
    ) {
        return Commands.argument("grave_id", StringArgumentType.word())
                .suggests((context, builder) -> suggestGraves(context, builder, allGraves));
    }

    private int showOwnGraves(CommandSender sender, boolean listAll) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        List<GraveSnapshot> graves = manager.findActiveByOwner(player.getUniqueId());
        if (graves.isEmpty()) {
            send(sender, "graveyard.no_graves");
            return 1;
        }
        if (!listAll) {
            showSnapshot(sender, graves.getFirst());
            return 1;
        }
        send(sender, "graveyard.list_header");
        for (GraveSnapshot grave : graves) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.list_entry")
                    .with("grave_id", grave.shortId())
                    .with("state", grave.status().name())
                    .with("world", grave.worldKey())
                    .with("x", coordinate(grave.x()))
                    .with("y", coordinate(grave.y()))
                    .with("z", coordinate(grave.z()))
                    .with("remaining", formatDuration(grave.remainingActiveMillis()))
                    .forAudience(sender)
                    .build());
        }
        return 1;
    }

    private int showInfo(CommandSender sender, String identifier) {
        Grave grave = resolveAccessible(sender, identifier, sender.hasPermission(P_ADMIN));
        if (grave == null) {
            return 1;
        }
        showSnapshot(sender, manager.find(grave.graveId()).orElseThrow());
        return 1;
    }

    private void showSnapshot(CommandSender sender, GraveSnapshot grave) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("graveyard.info")
                .with("grave_id", grave.shortId())
                .with("player", grave.ownerName())
                .with("state", grave.status().name())
                .with("world", grave.worldKey())
                .with("x", coordinate(grave.x()))
                .with("y", coordinate(grave.y()))
                .with("z", coordinate(grave.z()))
                .with("items", grave.itemEntryCount())
                .with("xp", grave.remainingExperience())
                .with("remaining", formatDuration(grave.remainingActiveMillis()))
                .forAudience(sender)
                .build());
    }

    private int locate(CommandSender sender, String identifier) {
        Grave grave = resolveAccessible(sender, identifier, false);
        if (grave == null) {
            return 1;
        }
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("graveyard.locate")
                .with("grave_id", grave.shortId())
                .with("world", grave.location().worldKey())
                .with("x", coordinate(grave.location().x()))
                .with("y", coordinate(grave.location().y()))
                .with("z", coordinate(grave.location().z()))
                .forAudience(sender)
                .build());
        return 1;
    }

    private int track(CommandSender sender, String identifier) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, false);
        if (grave == null || !manager.track(player, grave)) {
            send(sender, "graveyard.not_found");
            return 1;
        }
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("graveyard.track_started")
                .with("grave_id", grave.shortId())
                .forAudience(player)
                .build());
        return 1;
    }

    private int stopTracking(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        manager.stopTracking(player);
        send(sender, "graveyard.track_stopped");
        return 1;
    }

    private int claimRemote(CommandSender sender, String identifier) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, false);
        if (grave == null) {
            return 1;
        }
        manager.requestClaim(grave.graveId(), player.getUniqueId(), ClaimReason.REMOTE_UNREACHABLE)
                .thenAccept(result -> {
                    if (result.outcome() == GraveClaimOutcome.NOT_CLAIMABLE) {
                        player.sendMessage(feature.getLocalizationHandler()
                                .getMessage("graveyard.remote_claim_unavailable")
                                .forAudience(player)
                                .build());
                    }
                });
        return 1;
    }

    private int teleport(CommandSender sender, String identifier) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, true);
        if (grave == null) {
            return 1;
        }
        grave.location().resolve().ifPresentOrElse(
                location -> player.teleportAsync(location.clone().add(0.0, 1.0, 0.0)),
                () -> send(player, "graveyard.not_found")
        );
        return 1;
    }

    private int relocate(CommandSender sender, String identifier) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, true);
        if (grave == null) {
            return 1;
        }
        manager.relocate(player, grave, player.getLocation()).thenAccept(success ->
                sendAdminResult(player, grave.shortId(), success)
        );
        return 1;
    }

    private int deliver(CommandSender sender, String identifier) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, true);
        if (grave == null) {
            return 1;
        }
        manager.deliver(player, grave).thenAccept(result -> sendAdminResult(
                player,
                grave.shortId(),
                result.outcome() != GraveClaimOutcome.FAILED
        ));
        return 1;
    }

    private int transition(CommandSender sender, String identifier, AdminAction action) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.player_command");
            return 1;
        }
        Grave grave = resolveAccessible(sender, identifier, true);
        if (grave == null) {
            return 1;
        }
        CompletableFuture<Boolean> completion = switch (action) {
            case EXPIRE -> manager.expire(player, grave).toCompletableFuture();
            case RESTORE -> manager.restore(player, grave).toCompletableFuture();
            case PURGE -> manager.purge(player, grave).toCompletableFuture();
        };
        completion.thenAccept(success -> sendAdminResult(player, grave.shortId(), success));
        return 1;
    }

    private int diagnostics(CommandSender sender) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("graveyard.admin_diagnostics")
                .with("details", manager.diagnostics())
                .forAudience(sender)
                .build());
        return 1;
    }

    private Grave resolveAccessible(CommandSender sender, String identifier, boolean admin) {
        Grave grave = manager.findRuntime(identifier).orElse(null);
        if (grave == null) {
            send(sender, "graveyard.not_found");
            return null;
        }
        if (!admin) {
            if (!(sender instanceof Player player) || !grave.ownerUuid().equals(player.getUniqueId())) {
                send(sender, "graveyard.not_found");
                return null;
            }
        }
        return grave;
    }

    private CompletableFuture<Suggestions> suggestGraves(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            boolean allGraves
    ) {
        CommandSender sender = context.getSource().getSender();
        String prefix = builder.getRemaining().toUpperCase(Locale.ROOT);
        List<GraveSnapshot> candidates;
        if (allGraves && sender.hasPermission(P_ADMIN)) {
            candidates = manager.allRuntimeGraves();
        } else if (sender instanceof Player player) {
            candidates = manager.findActiveByOwner(player.getUniqueId());
        } else {
            candidates = List.of();
        }
        for (GraveSnapshot grave : candidates) {
            if (grave.shortId().toUpperCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(grave.shortId());
            }
        }
        return builder.buildFuture();
    }

    private void sendAdminResult(CommandSender sender, String graveId, boolean success) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(success ? "graveyard.admin_success" : "graveyard.admin_failed")
                .with("grave_id", graveId)
                .forAudience(sender)
                .build());
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(sender)
                .build());
    }

    private static String coordinate(double value) {
        return Integer.toString((int) Math.floor(value));
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + remainder + "s";
    }

    private enum AdminAction {
        EXPIRE,
        RESTORE,
        PURGE
    }
}
