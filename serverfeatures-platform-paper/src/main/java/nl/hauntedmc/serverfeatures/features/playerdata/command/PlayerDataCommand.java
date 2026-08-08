package nl.hauntedmc.serverfeatures.features.playerdata.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.playerdata.PlayerData;
import nl.hauntedmc.serverfeatures.features.playerdata.service.PlayerDataService.View;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class PlayerDataCommand implements BrigadierCommand {

    private final PlayerData feature;

    public PlayerDataCommand(PlayerData feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "playerdata";
    }

    @Override
    public String description() {
        return "Inspect live and offline playerdata without modifying it.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(PlayerData.INSPECT_PERMISSION))
                .executes(context -> {
                    feature.send(context.getSource().getSender(), "playerdata.usage");
                    return 1;
                })
                .then(targetArgument())
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetArgument() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> suggestPlayers(builder))
                .executes(context -> inspect(context.getSource(), context, View.OVERVIEW, null))
                .then(Commands.literal("overview")
                        .executes(context -> inspect(context.getSource(), context, View.OVERVIEW, null)))
                .then(Commands.literal("runtime")
                        .executes(context -> inspect(context.getSource(), context, View.RUNTIME, null)))
                .then(Commands.literal("settings")
                        .executes(context -> inspect(context.getSource(), context, View.SETTINGS, null)))
                .then(Commands.literal("pdc")
                        .executes(context -> inspect(context.getSource(), context, View.PDC, null)))
                .then(Commands.literal("nbt")
                        .executes(context -> inspect(context.getSource(), context, View.NBT, null))
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        context,
                                        View.NBT,
                                        StringArgumentType.getString(context, "path")
                                ))));
    }

    private int inspect(
            CommandSourceStack source,
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            View view,
            String path
    ) {
        feature.service().inspect(
                source.getSender(),
                StringArgumentType.getString(context, "player"),
                view,
                path
        );
        return 1;
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
