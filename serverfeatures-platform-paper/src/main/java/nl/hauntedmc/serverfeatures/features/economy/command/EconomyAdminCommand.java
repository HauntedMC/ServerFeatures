package nl.hauntedmc.serverfeatures.features.economy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Declares the administrator command tree and delegates execution to {@link EconomyAdminActions}. */
public final class EconomyAdminCommand implements BrigadierCommand {
    private final EconomyAdminActions actions;

    public EconomyAdminCommand(Economy feature) {
        this.actions = new EconomyAdminActions(feature);
    }

    @Override public @NotNull String name() { return "economy"; }
    @Override public String description() { return "Inspect and administer Economy accounts."; }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> hasAnyAdminPermission(source.getSender()))
                .executes(context -> actions.statusIfPermitted(context.getSource().getSender()));
        root.then(Commands.literal("help").executes(context -> actions.help(context.getSource().getSender())));
        root.then(Commands.literal("status").requires(source -> allowed(source, "status"))
                .executes(context -> actions.status(context.getSource().getSender())));
        root.then(Commands.literal("currencies").requires(source -> allowed(source, "status"))
                .executes(context -> actions.currencies(context.getSource().getSender())));
        root.then(Commands.literal("balance").requires(source -> allowed(source, "balance"))
                .then(playerCurrencyArguments(actions::balance)));
        root.then(Commands.literal("account").requires(source -> allowed(source, "balance"))
                .then(playerCurrencyArguments(actions::account)));
        root.then(mutation("add", TransactionType.DEPOSIT));
        root.then(mutation("remove", TransactionType.WITHDRAW));
        root.then(mutation("set", TransactionType.SET));
        root.then(Commands.literal("payments").requires(source -> allowed(source, "payments"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(paymentState("on", true))
                                .then(paymentState("off", false)))));
        root.then(freeze("freeze", true));
        root.then(freeze("unfreeze", false));
        root.then(Commands.literal("history").requires(source -> allowed(source, "history"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .executes(context -> actions.history(context.getSource().getSender(),
                                        word(context, "player"), word(context, "currency"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 10_000))
                                        .executes(context -> actions.history(context.getSource().getSender(),
                                                word(context, "player"), word(context, "currency"),
                                                IntegerArgumentType.getInteger(context, "page")))))));
        root.then(Commands.literal("verify").requires(source -> allowed(source, "verify"))
                .executes(context -> actions.verify(context.getSource().getSender())));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> mutation(String action, TransactionType type) {
        return Commands.literal(action).requires(source -> allowed(source, action))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> actions.mutate(context.getSource().getSender(),
                                                        word(context, "player"), word(context, "currency"),
                                                        word(context, "amount"), word(context, "reason"), type))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> freeze(String literal, boolean frozen) {
        return Commands.literal(literal).requires(source -> allowed(source, "freeze"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> actions.freeze(context.getSource().getSender(),
                                                word(context, "player"), word(context, "currency"),
                                                word(context, "reason"), frozen)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> paymentState(String literal, boolean enabled) {
        return Commands.literal(literal).then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> actions.payments(context.getSource().getSender(), word(context, "player"),
                        word(context, "currency"), enabled, word(context, "reason"))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> playerCurrencyArguments(AdminAction action) {
        return Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("currency", StringArgumentType.word())
                        .executes(context -> action.execute(context.getSource().getSender(),
                                word(context, "player"), word(context, "currency"))));
    }

    private boolean hasAnyAdminPermission(CommandSender sender) {
        return EconomyCommandPermissions.hasAnyAdminPermission(sender);
    }

    private static boolean allowed(CommandSourceStack source, String action) {
        return EconomyCommandPermissions.adminAction(source.getSender(), action);
    }

    private static String word(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    @FunctionalInterface
    private interface AdminAction {
        int execute(CommandSender sender, String player, String currency);
    }
}
