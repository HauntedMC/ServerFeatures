package nl.hauntedmc.serverfeatures.features.combattag.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagSnapshot;
import nl.hauntedmc.serverfeatures.api.combat.CombatUntagReason;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.combattag.CombatTag;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CombatTagCommand implements BrigadierCommand {

    private final CombatTag feature;

    public CombatTagCommand(CombatTag feature) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
    }

    @Override
    public @NotNull String name() {
        return "combattag";
    }

    @Override
    public String description() {
        return "Inspect or administratively clear combat tags.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal(name())
                .then(Commands.literal("status")
                        .requires(source -> source.getSender().hasPermission(CombatTag.STATUS_PERMISSION))
                        .executes(context -> statusSelf(context.getSource().getSender()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(source -> source.getSender().hasPermission(
                                        CombatTag.STATUS_OTHERS_PERMISSION
                                ))
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .executes(context -> statusOther(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .then(Commands.literal("untag")
                        .requires(source -> source.getSender().hasPermission(CombatTag.UNTAG_PERMISSION))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .executes(context -> untag(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .build();
    }

    private int statusSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            feature.sendMessage(sender, "combattag.command.player-only");
            return 0;
        }
        sendStatus(sender, player, false);
        return 1;
    }

    private int statusOther(CommandSender sender, String playerName) {
        Player player = onlinePlayer(sender, playerName);
        if (player == null) {
            return 0;
        }
        sendStatus(sender, player, true);
        return 1;
    }

    private int untag(CommandSender sender, String playerName) {
        Player player = onlinePlayer(sender, playerName);
        if (player == null) {
            return 0;
        }
        boolean removed = feature.service().untag(player, CombatUntagReason.ADMINISTRATIVE);
        feature.sendMessage(
                sender,
                removed ? "combattag.command.untagged" : "combattag.command.already-untagged",
                Map.of("target", player.getName())
        );
        return removed ? 1 : 0;
    }

    private void sendStatus(CommandSender sender, Player player, boolean other) {
        Optional<CombatTagSnapshot> snapshot = feature.service().getTag(player);
        if (snapshot.isEmpty()) {
            feature.sendMessage(
                    sender,
                    other
                            ? "combattag.command.status-other.not-tagged"
                            : "combattag.command.status.not-tagged",
                    Map.of("target", player.getName())
            );
            return;
        }

        CombatTagSnapshot tag = snapshot.get();
        long seconds = Math.max(1L, (tag.remaining().toMillis() + 999L) / 1_000L);
        feature.sendMessage(
                sender,
                other
                        ? "combattag.command.status-other.tagged"
                        : "combattag.command.status.tagged",
                Map.of(
                        "target", player.getName(),
                        "seconds", Long.toString(seconds),
                        "opponent", tag.opponent().displayName(),
                        "reason", tag.reason().name().toLowerCase(Locale.ROOT)
                )
        );
    }

    private Player onlinePlayer(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            feature.sendMessage(
                    sender,
                    "combattag.command.player-not-found",
                    Map.of("target", playerName)
            );
            return null;
        }
        return player;
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
