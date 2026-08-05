package nl.hauntedmc.serverfeatures.features.fairperks.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.service.PerkStateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FairPerksCommand implements BrigadierCommand {

    private final FairPerks feature;

    public FairPerksCommand(FairPerks feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "fairperks";
    }

    @Override
    public String description() {
        return "Inspect authoritative FairPerks state.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        return Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(FairPerks.INSPECT_PERMISSION))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestPlayers(builder))
                                .executes(context -> inspect(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .build();
    }

    private int inspect(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            feature.sendMessage(sender, "fairperks.player_not_found", Map.of("target", playerName));
            return 0;
        }
        PerkStateService.RuntimeView view = feature.stateService().view(player);
        feature.sendMessage(sender, "fairperks.inspect", Map.of(
                "target", player.getName(),
                "fly_desired", Boolean.toString(view.flyDesired()),
                "fly_effective", Boolean.toString(view.flyEffective()),
                "fly_owned", Boolean.toString(view.flightOwned()),
                "god_desired", Boolean.toString(view.godDesired()),
                "god_effective", Boolean.toString(view.godEffective()),
                "macro", Boolean.toString(view.godMacroEnabled()),
                "fall_grace", Boolean.toString(view.fallDamageGrace())
        ));
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
