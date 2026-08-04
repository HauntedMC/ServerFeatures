package nl.hauntedmc.serverfeatures.features.graveyard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.graveyard.ClaimReason;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveClaimOutcome;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import nl.hauntedmc.serverfeatures.features.graveyard.text.GraveyardText;
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
    private static final String P_ADMIN_LIST = P_ADMIN + ".list";
    private static final String P_INSPECT = P_ADMIN + ".inspect";
    private static final String P_TELEPORT = P_ADMIN + ".teleport";
    private static final String P_RELOCATE = P_ADMIN + ".relocate";
    private static final String P_DELIVER = P_ADMIN + ".deliver";
    private static final String P_EXPIRE = P_ADMIN + ".expire";
    private static final String P_RESTORE = P_ADMIN + ".restore";
    private static final String P_PURGE = P_ADMIN + ".purge";
    private static final String P_DIAGNOSTICS = P_ADMIN + ".diagnostics";

    private final Graveyard feature;
    private final GraveManager manager;
    private final GraveyardText text;

    public GraveCommand(Graveyard feature, GraveManager manager) {
        this.feature = feature;
        this.manager = manager;
        this.text = new GraveyardText(feature);
    }

    @Override
    public @NotNull String name() {
        return "grave";
    }

    @Override
    public String description() {
        return "Find and recover virtual death graves.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> hasRootAccess(source.getSender()))
                .executes(context -> showOwnGraves(context.getSource().getSender(), false));

        root.then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission(P_LIST))
                .executes(context -> showOwnGraves(context.getSource().getSender(), true)));

        root.then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission(P_INFO))
                .then(graveArgument(SuggestionScope.OWNER_ACTIVE).executes(context -> showInfo(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("locate")
                .requires(source -> source.getSender().hasPermission(P_LOCATE))
                .then(graveArgument(SuggestionScope.OWNER_ACTIVE).executes(context -> locate(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("track")
                .requires(source -> source.getSender().hasPermission(P_TRACK))
                .then(Commands.literal("off").executes(context -> stopTracking(
                        context.getSource().getSender()
                )))
                .then(graveArgument(SuggestionScope.OWNER_ACTIVE).executes(context -> track(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission(P_REMOTE_CLAIM))
                .then(graveArgument(SuggestionScope.OWNER_ACTIVE).executes(context -> claimRemote(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "grave_id")
                ))));

        root.then(adminTree());
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminTree() {
        return Commands.literal("admin")
                .requires(source -> hasAdminAccess(source.getSender()))
                .then(Commands.literal("list")
                        .requires(source -> hasPermission(source.getSender(), P_ADMIN_LIST))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> listPlayerGraves(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .then(Commands.literal("diagnostics")
                        .requires(source -> hasPermission(source.getSender(), P_DIAGNOSTICS))
                        .executes(context -> diagnostics(context.getSource().getSender())))
                .then(Commands.literal("info")
                        .requires(source -> hasPermission(source.getSender(), P_INSPECT))
                        .then(graveArgument(SuggestionScope.ADMIN_INSPECTABLE).executes(context -> showInfo(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("teleport")
                        .requires(source -> hasPermission(source.getSender(), P_TELEPORT))
                        .then(graveArgument(SuggestionScope.ADMIN_ACTIVE).executes(context -> teleport(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("relocate")
                        .requires(source -> hasPermission(source.getSender(), P_RELOCATE))
                        .then(graveArgument(SuggestionScope.ADMIN_ACTIVE).executes(context -> relocate(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("deliver")
                        .requires(source -> hasPermission(source.getSender(), P_DELIVER))
                        .then(graveArgument(SuggestionScope.ADMIN_ACTIVE).executes(context -> deliver(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id")
                        ))))
                .then(Commands.literal("expire")
                        .requires(source -> hasPermission(source.getSender(), P_EXPIRE))
                        .then(graveArgument(SuggestionScope.ADMIN_ACTIVE).executes(context -> transition(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id"),
                                AdminAction.EXPIRE
                        ))))
                .then(Commands.literal("restore")
                        .requires(source -> hasPermission(source.getSender(), P_RESTORE))
                        .then(graveArgument(SuggestionScope.ADMIN_RESTORABLE).executes(context -> transition(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "grave_id"),
                                AdminAction.RESTORE
                        ))))
                .then(Commands.literal("purge")
                        .requires(source -> hasPermission(source.getSender(), P_PURGE))
                        .then(Commands.literal("confirm")
                                .then(graveArgument(SuggestionScope.ADMIN_PURGEABLE).executes(context -> transition(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "grave_id"),
                                        AdminAction.PURGE
                                )))));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> graveArgument(
            SuggestionScope scope
    ) {
        return Commands.argument("grave_id", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestGraves(context, builder, scope));
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
                    .with("state", text.status(grave.status(), sender))
                    .with("world", grave.worldKey())
                    .with("x", coordinate(grave.x()))
                    .with("y", coordinate(grave.y()))
                    .with("z", coordinate(grave.z()))
                    .with("remaining", text.duration(grave.remainingActiveMillis(), sender))
                    .forAudience(sender)
                    .build());
        }
        return 1;
    }

    private int listPlayerGraves(CommandSender sender, String playerIdentifier) {
        List<GraveSnapshot> graves = manager.findByOwnerIdentifier(playerIdentifier);
        if (graves.isEmpty()) {
            send(sender, "graveyard.admin_list_empty");
            return 1;
        }
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("graveyard.admin_list_header")
                .with("player", graves.getFirst().ownerName())
                .forAudience(sender)
                .build());
        for (GraveSnapshot grave : graves) {
            sender.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.list_entry")
                    .with("grave_id", grave.shortId())
                    .with("state", text.status(grave.status(), sender))
                    .with("world", grave.worldKey())
                    .with("x", coordinate(grave.x()))
                    .with("y", coordinate(grave.y()))
                    .with("z", coordinate(grave.z()))
                    .with("remaining", text.duration(grave.remainingActiveMillis(), sender))
                    .forAudience(sender)
                    .build());
        }
        return 1;
    }

    private int showInfo(CommandSender sender, String identifier) {
        SuggestionScope scope = hasPermission(sender, P_INSPECT)
                ? SuggestionScope.ADMIN_INSPECTABLE
                : SuggestionScope.OWNER_ACTIVE;
        Grave grave = resolveAccessible(sender, identifier, scope);
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
                .with("state", text.status(grave.status(), sender))
                .with("world", grave.worldKey())
                .with("x", coordinate(grave.x()))
                .with("y", coordinate(grave.y()))
                .with("z", coordinate(grave.z()))
                .with("items", grave.itemEntryCount())
                .with("xp", grave.remainingExperience())
                .with("remaining", text.duration(grave.remainingActiveMillis(), sender))
                .forAudience(sender)
                .build());
    }

    private int locate(CommandSender sender, String identifier) {
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.OWNER_ACTIVE);
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
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.OWNER_ACTIVE);
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
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.OWNER_ACTIVE);
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
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.ADMIN_ACTIVE);
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
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.ADMIN_ACTIVE);
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
        Grave grave = resolveAccessible(sender, identifier, SuggestionScope.ADMIN_ACTIVE);
        if (grave == null) {
            return 1;
        }
        manager.deliver(player, grave).thenAccept(result -> sendAdminResult(
                player,
                grave.shortId(),
                result.outcome() == GraveClaimOutcome.CLAIMED
                        || result.outcome() == GraveClaimOutcome.PARTIAL
                        || result.outcome() == GraveClaimOutcome.DELIVERY_QUEUED
                        || result.outcome() == GraveClaimOutcome.RECOVERY_PENDING
        ));
        return 1;
    }

    private int transition(CommandSender sender, String identifier, AdminAction action) {
        SuggestionScope scope = switch (action) {
            case EXPIRE -> SuggestionScope.ADMIN_ACTIVE;
            case RESTORE -> SuggestionScope.ADMIN_RESTORABLE;
            case PURGE -> SuggestionScope.ADMIN_PURGEABLE;
        };
        Grave grave = resolveAccessible(sender, identifier, scope);
        if (grave == null) {
            return 1;
        }
        Player actor = sender instanceof Player player ? player : null;
        CompletableFuture<Boolean> completion = switch (action) {
            case EXPIRE -> manager.expire(actor, grave).toCompletableFuture();
            case RESTORE -> manager.restore(actor, grave).toCompletableFuture();
            case PURGE -> manager.purge(actor, grave).toCompletableFuture();
        };
        completion.thenAccept(success -> sendAdminResult(sender, grave.shortId(), success));
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

    private Grave resolveAccessible(
            CommandSender sender,
            String identifier,
            SuggestionScope scope
    ) {
        Grave grave = manager.findRuntime(identifier)
                .filter(candidate -> scope.accepts(candidate.status()))
                .orElse(null);
        if (grave == null) {
            send(sender, "graveyard.not_found");
            return null;
        }
        if (scope == SuggestionScope.OWNER_ACTIVE
                && (!(sender instanceof Player player)
                || !grave.ownerUuid().equals(player.getUniqueId()))) {
            send(sender, "graveyard.not_found");
            return null;
        }
        return grave;
    }

    private CompletableFuture<Suggestions> suggestGraves(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            SuggestionScope scope
    ) {
        CommandSender sender = context.getSource().getSender();
        String prefix = builder.getRemaining().toUpperCase(Locale.ROOT);
        List<GraveSnapshot> candidates = switch (scope) {
            case OWNER_ACTIVE -> sender instanceof Player player
                    ? manager.findActiveByOwner(player.getUniqueId())
                    : List.of();
            case ADMIN_INSPECTABLE -> hasAdminAccess(sender)
                    ? manager.allRuntimeGraves()
                    : List.of();
            case ADMIN_ACTIVE -> hasAdminAccess(sender)
                    ? manager.allActiveRuntimeGraves()
                    : List.of();
            case ADMIN_RESTORABLE -> hasAdminAccess(sender)
                    ? manager.allRestorableRuntimeGraves()
                    : List.of();
            case ADMIN_PURGEABLE -> hasAdminAccess(sender)
                    ? manager.allPurgeableRuntimeGraves()
                    : List.of();
        };
        for (GraveSnapshot grave : candidates) {
            if (grave.shortId().toUpperCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(grave.shortId());
            }
        }
        return builder.buildFuture();
    }

    private static boolean hasRootAccess(CommandSender sender) {
        return sender.hasPermission(BASE) || hasAdminAccess(sender);
    }

    private static boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(P_ADMIN) || sender.hasPermission(permission);
    }

    private static boolean hasAdminAccess(CommandSender sender) {
        return sender.hasPermission(P_ADMIN)
                || sender.hasPermission(P_ADMIN_LIST)
                || sender.hasPermission(P_INSPECT)
                || sender.hasPermission(P_TELEPORT)
                || sender.hasPermission(P_RELOCATE)
                || sender.hasPermission(P_DELIVER)
                || sender.hasPermission(P_EXPIRE)
                || sender.hasPermission(P_RESTORE)
                || sender.hasPermission(P_PURGE)
                || sender.hasPermission(P_DIAGNOSTICS);
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

    private enum SuggestionScope {
        OWNER_ACTIVE {
            @Override
            boolean accepts(GraveStatus status) {
                return GraveManager.isOwnerListable(status);
            }
        },
        ADMIN_INSPECTABLE {
            @Override
            boolean accepts(GraveStatus status) {
                return status != GraveStatus.PURGED;
            }
        },
        ADMIN_ACTIVE {
            @Override
            boolean accepts(GraveStatus status) {
                return GraveManager.isOwnerListable(status);
            }
        },
        ADMIN_RESTORABLE {
            @Override
            boolean accepts(GraveStatus status) {
                return GraveManager.isRestorable(status);
            }
        },
        ADMIN_PURGEABLE {
            @Override
            boolean accepts(GraveStatus status) {
                return GraveManager.isPurgeable(status);
            }
        };

        abstract boolean accepts(GraveStatus status);
    }

    private enum AdminAction {
        EXPIRE,
        RESTORE,
        PURGE
    }
}
