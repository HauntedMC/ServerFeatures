package nl.hauntedmc.serverfeatures.features.autopickup.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AutoPickupCommand implements BrigadierCommand {

    private final AutoPickup feature;

    public AutoPickupCommand(AutoPickup feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "autopickup";
    }

    @Override
    public String description() {
        return "Toggle automatic collection of directly mined block drops.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(AutoPickup.USE_PERMISSION))
                .executes(context -> execute(context.getSource().getSender(), CommandIntent.TOGGLE))
                .then(Commands.literal("on")
                        .executes(context -> execute(context.getSource().getSender(), CommandIntent.ENABLE)))
                .then(Commands.literal("off")
                        .executes(context -> execute(context.getSource().getSender(), CommandIntent.DISABLE)))
                .then(Commands.literal("toggle")
                        .executes(context -> execute(context.getSource().getSender(), CommandIntent.TOGGLE)))
                .then(Commands.literal("status")
                        .executes(context -> execute(context.getSource().getSender(), CommandIntent.STATUS)));
        return root.build();
    }

    private int execute(CommandSender sender, CommandIntent intent) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return 0;
        }
        feature.preferences().handleCommand(player, intent);
        return 1;
    }
}
