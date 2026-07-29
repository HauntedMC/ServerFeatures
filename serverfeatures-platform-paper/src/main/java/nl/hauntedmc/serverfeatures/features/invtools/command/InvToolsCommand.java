package nl.hauntedmc.serverfeatures.features.invtools.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Staff inventory tooling rooted at {@code /inv}. Permissions are attached to their precise
 * operation so unavailable actions are not suggested to a sender.
 */
public final class InvToolsCommand implements BrigadierCommand {

    private final InvTools feature;

    public InvToolsCommand(InvTools feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "inv";
    }

    @Override
    public String description() {
        return "Inspect, edit, or clear player inventories and ender chests.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name());
        root.then(inventoryBranch("inventory", InventoryKind.PLAYER));
        root.then(inventoryBranch("enderchest", InventoryKind.ENDER_CHEST));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> inventoryBranch(
            String literal,
            InventoryKind kind
    ) {
        return Commands.literal(literal)
                .then(Commands.literal("open")
                        .requires(source -> isPlayerWith(
                                source,
                                InvToolsService.openInspectPermission(kind)
                        ))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .executes(context -> {
                                    Player player = player(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    feature.getService().open(
                                            player,
                                            StringArgumentType.getString(context, "player"),
                                            kind
                                    );
                                    return 1;
                                })))
                .then(Commands.literal("clear")
                        .requires(source -> isPlayerWith(
                                source,
                                InvToolsService.clearPermission(kind)
                        ))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .executes(context -> {
                                    Player player = player(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    feature.getService().clear(
                                            player,
                                            StringArgumentType.getString(context, "player"),
                                            kind
                                    );
                                    return 1;
                                })));
    }

    private static boolean isPlayerWith(CommandSourceStack source, String permission) {
        return source.getSender() instanceof Player player && player.hasPermission(permission);
    }

    private static Player player(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        return sender instanceof Player player ? player : null;
    }

    private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(20)
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
