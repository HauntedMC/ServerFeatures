package nl.hauntedmc.serverfeatures.features.phantomtoggle.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.PhantomToggle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PhantomToggleCommand implements BrigadierCommand {

    private final PhantomToggle feature;

    public PhantomToggleCommand(PhantomToggle feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "phantomtoggle";
    }

    @Override
    public List<String> aliases() {
        return List.of("phantoms");
    }

    @Override
    public String description() {
        return "Toggle whether insomnia phantoms can spawn for you.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(PhantomToggle.USE_PERMISSION))
                .executes(context -> execute(context.getSource().getSender(), Intent.TOGGLE))
                .then(Commands.literal("on")
                        .executes(context -> execute(context.getSource().getSender(), Intent.ENABLE)))
                .then(Commands.literal("off")
                        .executes(context -> execute(context.getSource().getSender(), Intent.DISABLE)))
                .then(Commands.literal("toggle")
                        .executes(context -> execute(context.getSource().getSender(), Intent.TOGGLE)))
                .then(Commands.literal("status")
                        .executes(context -> execute(context.getSource().getSender(), Intent.STATUS)));
        return root.build();
    }

    private int execute(CommandSender sender, Intent intent) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command")
                    .forAudience(sender)
                    .build());
            return 0;
        }

        boolean current = feature.preferences().phantomsEnabled(player);
        if (intent == Intent.STATUS) {
            feature.sendPlayerMessage(player, current
                    ? "phantomtoggle.status.enabled"
                    : "phantomtoggle.status.disabled");
            return 1;
        }

        boolean desired = switch (intent) {
            case ENABLE -> true;
            case DISABLE -> false;
            case TOGGLE -> !current;
            case STATUS -> throw new IllegalStateException("STATUS was handled before mutation");
        };

        if (desired == current) {
            feature.sendPlayerMessage(player, desired
                    ? "phantomtoggle.already_enabled"
                    : "phantomtoggle.already_disabled");
            return 1;
        }

        feature.preferences().setPhantomsEnabled(player, desired);
        feature.sendPlayerMessage(player, desired
                ? "phantomtoggle.enabled"
                : "phantomtoggle.disabled");
        return 1;
    }

    private enum Intent {
        ENABLE,
        DISABLE,
        TOGGLE,
        STATUS
    }
}
