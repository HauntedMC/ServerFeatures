package nl.hauntedmc.serverfeatures.features.fairperks.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkChangeResult;
import nl.hauntedmc.serverfeatures.features.fairperks.model.PerkType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class PerkCommand implements BrigadierCommand {

    private final FairPerks feature;
    private final PerkType perk;

    public PerkCommand(FairPerks feature, PerkType perk) {
        this.feature = feature;
        this.perk = perk;
    }

    @Override
    public @NotNull String name() {
        return perk.key();
    }

    @Override
    public List<String> aliases() {
        return perk == PerkType.FLY
                ? feature.settings().commands().flyAliases()
                : feature.settings().commands().godAliases();
    }

    @Override
    public String description() {
        return perk == PerkType.FLY
                ? "Toggle native FairPerks flight."
                : "Toggle native FairPerks god mode.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> canAccessRoot(source.getSender()))
                .executes(context -> executeSelf(context.getSource().getSender(), Intent.TOGGLE));

        root.then(selfLiteral("on", Intent.ENABLE));
        root.then(selfLiteral("off", Intent.DISABLE));
        root.then(selfLiteral("toggle", Intent.TOGGLE));
        root.then(selfLiteral("status", Intent.STATUS));

        root.then(Commands.argument("player", StringArgumentType.word())
                .requires(source -> source.getSender().hasPermission(othersPermission()))
                .suggests((context, builder) -> suggestPlayers(builder))
                .executes(context -> executeTarget(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "player"),
                        Intent.TOGGLE
                ))
                .then(targetLiteral("on", Intent.ENABLE))
                .then(targetLiteral("off", Intent.DISABLE))
                .then(targetLiteral("toggle", Intent.TOGGLE))
                .then(targetLiteral("status", Intent.STATUS)));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> selfLiteral(String literal, Intent intent) {
        return Commands.literal(literal)
                .executes(context -> executeSelf(context.getSource().getSender(), intent));
    }

    private LiteralArgumentBuilder<CommandSourceStack> targetLiteral(String literal, Intent intent) {
        return Commands.literal(literal)
                .executes(context -> executeTarget(
                        context.getSource().getSender(),
                        StringArgumentType.getString(context, "player"),
                        intent
                ));
    }

    private boolean canAccessRoot(CommandSender sender) {
        if (sender.hasPermission(usePermission()) || sender.hasPermission(othersPermission())) {
            return true;
        }
        return sender instanceof Player player && feature.stateService().isDesired(player, perk);
    }

    private int executeSelf(CommandSender sender, Intent intent) {
        if (!(sender instanceof Player player)) {
            feature.sendMessage(sender, "fairperks.player_only");
            return 0;
        }

        feature.stateService().initializeIfAbsent(player);
        boolean current = feature.stateService().isDesired(player, perk);
        boolean mayOperateWithoutPermission = current
                && (intent == Intent.DISABLE || intent == Intent.TOGGLE || intent == Intent.STATUS);
        if (!player.hasPermission(usePermission()) && !mayOperateWithoutPermission) {
            feature.sendMessage(player, "fairperks.no_permission");
            return 0;
        }
        if (intent == Intent.STATUS) {
            sendStatus(player, player);
            return 1;
        }

        PerkChangeResult result = apply(
                player,
                intent,
                player.hasPermission(bypassPermission()),
                false
        );
        sendSelfResult(player, result);
        return result.success() ? 1 : 0;
    }

    private int executeTarget(CommandSender actor, String targetName, Intent intent) {
        if (!actor.hasPermission(othersPermission())) {
            feature.sendMessage(actor, "fairperks.no_permission");
            return 0;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            feature.sendMessage(actor, "fairperks.player_not_found", Map.of("target", targetName));
            return 0;
        }
        feature.stateService().initializeIfAbsent(target);
        if (intent == Intent.STATUS) {
            sendStatus(actor, target);
            return 1;
        }

        PerkChangeResult result = apply(
                target,
                intent,
                actor.hasPermission(bypassPermission()),
                true
        );
        if (!result.success()) {
            sendDenied(actor, result.status());
            return 0;
        }

        boolean enabled = result.enabled();
        String stateKey = enabled ? "enabled" : "disabled";
        if (result.status() == PerkChangeResult.Status.ALREADY_IN_STATE) {
            feature.sendMessage(
                    actor,
                    "fairperks." + perk.key() + ".already_" + stateKey + "_other",
                    Map.of("target", target.getName())
            );
            return 1;
        }

        feature.sendMessage(
                actor,
                "fairperks." + perk.key() + "." + stateKey + "_other",
                Map.of("target", target.getName())
        );
        feature.sendMessage(
                target,
                "fairperks." + perk.key() + ".target_" + stateKey,
                Map.of("actor", actor.getName())
        );
        return 1;
    }

    private PerkChangeResult apply(
            Player player,
            Intent intent,
            boolean bypassActivationGuard,
            boolean bypassUsePermission
    ) {
        return switch (intent) {
            case ENABLE -> feature.stateService().set(
                    player,
                    perk,
                    true,
                    bypassActivationGuard,
                    bypassUsePermission
            );
            case DISABLE -> feature.stateService().set(
                    player,
                    perk,
                    false,
                    bypassActivationGuard,
                    bypassUsePermission
            );
            case TOGGLE -> feature.stateService().toggle(
                    player,
                    perk,
                    bypassActivationGuard,
                    bypassUsePermission
            );
            case STATUS -> throw new IllegalStateException("Status is handled before state mutation");
        };
    }

    private void sendSelfResult(Player player, PerkChangeResult result) {
        if (!result.success()) {
            sendDenied(player, result.status());
            return;
        }
        String state = result.enabled() ? "enabled" : "disabled";
        String key = result.status() == PerkChangeResult.Status.ALREADY_IN_STATE
                ? "fairperks." + perk.key() + ".already_" + state
                : "fairperks." + perk.key() + "." + state;
        feature.sendMessage(player, key);
    }

    private void sendStatus(CommandSender audience, Player target) {
        boolean enabled = feature.stateService().isDesired(target, perk);
        String state = enabled ? "enabled" : "disabled";
        if (audience instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId())) {
            feature.sendMessage(audience, "fairperks." + perk.key() + ".status_" + state);
            return;
        }
        feature.sendMessage(
                audience,
                "fairperks." + perk.key() + ".status_" + state + "_other",
                Map.of("target", target.getName())
        );
    }

    private void sendDenied(CommandSender audience, PerkChangeResult.Status status) {
        String key = switch (status) {
            case NO_PERMISSION -> "fairperks.no_permission";
            case COMBAT_TAGGED -> "fairperks.denied.combat";
            case HOSTILE_NEARBY -> "fairperks.denied.hostile";
            case WORLD_BLOCKED -> "fairperks.denied.world";
            case GAME_MODE_BLOCKED -> "fairperks.denied.game_mode";
            case CHANGED, ALREADY_IN_STATE -> throw new IllegalArgumentException(
                    "Cannot render a successful status as denied: " + status
            );
        };
        feature.sendMessage(audience, key);
    }

    private String usePermission() {
        return perk == PerkType.FLY ? FairPerks.FLY_USE_PERMISSION : FairPerks.GOD_USE_PERMISSION;
    }

    private String othersPermission() {
        return perk == PerkType.FLY ? FairPerks.FLY_OTHERS_PERMISSION : FairPerks.GOD_OTHERS_PERMISSION;
    }

    private String bypassPermission() {
        return perk == PerkType.FLY ? FairPerks.FLY_BYPASS_PERMISSION : FairPerks.GOD_BYPASS_PERMISSION;
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

    private enum Intent {
        ENABLE,
        DISABLE,
        TOGGLE,
        STATUS
    }
}
