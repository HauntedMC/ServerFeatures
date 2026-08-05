package nl.hauntedmc.serverfeatures.features.fairperks.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GodMacroCommand implements BrigadierCommand {

    private final FairPerks feature;

    public GodMacroCommand(FairPerks feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "godmacro";
    }

    @Override
    public List<String> aliases() {
        return feature.settings().commands().godMacroAliases();
    }

    @Override
    public String description() {
        return "Configure the FairPerks double-shift god macro.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> source.getSender().hasPermission(FairPerks.GOD_MACRO_PERMISSION))
                .executes(context -> execute(context.getSource().getSender(), Intent.TOGGLE));
        root.then(literal("on", Intent.ENABLE));
        root.then(literal("off", Intent.DISABLE));
        root.then(literal("toggle", Intent.TOGGLE));
        root.then(literal("status", Intent.STATUS));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> literal(String name, Intent intent) {
        return Commands.literal(name)
                .executes(context -> execute(context.getSource().getSender(), intent));
    }

    private int execute(CommandSender sender, Intent intent) {
        if (!(sender instanceof Player player)) {
            feature.sendMessage(sender, "fairperks.player_only");
            return 0;
        }
        if (!player.hasPermission(FairPerks.GOD_MACRO_PERMISSION)) {
            feature.sendMessage(player, "fairperks.no_permission");
            return 0;
        }

        boolean current = feature.stateService().isGodMacroEnabled(player);
        if (intent == Intent.STATUS) {
            feature.sendMessage(
                    player,
                    current ? "fairperks.godmacro.status_enabled" : "fairperks.godmacro.status_disabled"
            );
            return 1;
        }

        boolean enabled = switch (intent) {
            case ENABLE -> true;
            case DISABLE -> false;
            case TOGGLE -> !current;
            case STATUS -> throw new IllegalStateException();
        };
        PerkChangeResult result = feature.stateService().setGodMacro(player, enabled);
        if (!result.success()) {
            feature.sendMessage(player, switch (result.status()) {
                case WORLD_BLOCKED -> "fairperks.denied.world";
                case NO_PERMISSION -> "fairperks.no_permission";
                case COMBAT_TAGGED -> "fairperks.denied.combat";
                case HOSTILE_NEARBY -> "fairperks.denied.hostile";
                case GAME_MODE_BLOCKED -> "fairperks.denied.game_mode";
                case CHANGED, ALREADY_IN_STATE -> throw new IllegalStateException();
            });
            return 0;
        }
        String key;
        if (result.status() == PerkChangeResult.Status.CHANGED) {
            key = enabled ? "fairperks.godmacro.enabled" : "fairperks.godmacro.disabled";
        } else {
            key = enabled ? "fairperks.godmacro.already_enabled" : "fairperks.godmacro.already_disabled";
        }
        feature.sendMessage(player, key);
        return 1;
    }

    private enum Intent {
        ENABLE,
        DISABLE,
        TOGGLE,
        STATUS
    }
}
