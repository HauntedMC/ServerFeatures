package nl.hauntedmc.serverfeatures.features.economy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** One dynamically registered player command root for a configured currency. */
public final class CurrencyCommand implements BrigadierCommand {
    private static final int PAGE_SIZE = 10;

    private final Economy feature;
    private final EconomySettings.Currency currency;
    private final CurrencyPaymentHandler payments;

    public CurrencyCommand(Economy feature, EconomySettings.Currency currency) {
        this.feature = feature;
        this.currency = currency;
        this.payments = new CurrencyPaymentHandler(feature, currency);
    }

    @Override
    public @NotNull String name() {
        return currency.commands().root();
    }

    @Override
    public List<String> aliases() {
        return currency.commands().aliases();
    }

    @Override
    public String description() {
        return "Manage your " + currency.display().plural() + ".";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> canUseAnyCommand(source.getSender()));
        if (currency.commands().balance()) {
            root.executes(context -> EconomyCommandPermissions.canViewOwnBalance(context.getSource().getSender(), currency.id())
                    ? balance(context.getSource().getSender(), null) : help(context.getSource().getSender()));
            LiteralArgumentBuilder<CommandSourceStack> balance = Commands.literal("balance")
                    .requires(source -> EconomyCommandPermissions.canViewOwnBalance(source.getSender(), currency.id())
                            || currency.commands().balanceOthers()
                            && EconomyCommandPermissions.canViewAnyBalance(source.getSender(), currency.id()))
                    .executes(context -> EconomyCommandPermissions.canViewOwnBalance(context.getSource().getSender(), currency.id())
                            ? balance(context.getSource().getSender(), null) : help(context.getSource().getSender()));
            if (currency.commands().balanceOthers()) {
                balance.then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> EconomyCommandPermissions.canViewAnyBalance(source.getSender(), currency.id()))
                        .executes(context -> balance(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player")
                        )));
            }
            root.then(balance);
        }
        if (currency.commands().pay()) {
            root.then(Commands.literal("pay")
                    .requires(source -> allowed(source.getSender(), "pay"))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("amount", StringArgumentType.word())
                                    .executes(context -> payments.pay(
                                            context.getSource().getSender(),
                                            StringArgumentType.getString(context, "player"),
                                            StringArgumentType.getString(context, "amount")
                                    )))));
            if (currency.payments().confirmationThreshold().signum() > 0) {
                root.then(Commands.literal("confirm")
                        .requires(source -> allowed(source.getSender(), "pay"))
                        .executes(context -> payments.confirm(context.getSource().getSender())));
            }
        }
        if (currency.commands().paytoggle()) {
            LiteralArgumentBuilder<CommandSourceStack> toggle = Commands.literal("paytoggle")
                    .requires(source -> allowed(source.getSender(), "paytoggle"))
                    .executes(context -> showPayToggle(context.getSource().getSender()));
            toggle.then(Commands.literal("on").executes(context -> setPayToggle(context.getSource().getSender(), true)));
            toggle.then(Commands.literal("off").executes(context -> setPayToggle(context.getSource().getSender(), false)));
            toggle.then(Commands.literal("status").executes(context -> showPayToggle(context.getSource().getSender())));
            root.then(toggle);
        }
        if (currency.commands().history()) {
            root.then(Commands.literal("history")
                    .requires(source -> allowed(source.getSender(), "history"))
                    .executes(context -> history(context.getSource().getSender(), 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1, 10_000))
                            .executes(context -> history(
                                    context.getSource().getSender(),
                                    IntegerArgumentType.getInteger(context, "page")
                            ))));
        }
        if (currency.commands().top()) {
            root.then(Commands.literal("top")
                    .requires(source -> allowed(source.getSender(), "top"))
                    .executes(context -> top(context.getSource().getSender(), 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1, 10_000))
                            .executes(context -> top(
                                    context.getSource().getSender(),
                                    IntegerArgumentType.getInteger(context, "page")
                            ))));
        }
        root.then(Commands.literal("help").executes(context -> help(context.getSource().getSender())));
        return root.build();
    }

    private int balance(CommandSender sender, String target) {
        Player player = sender instanceof Player online ? online : null;
        if (target == null && player == null) {
            feature.send(sender, "economy.player_only");
            return 0;
        }
        String identifier = target == null ? player.getUniqueId().toString() : target;
        feature.service().resolveIdentifier(identifier)
                .thenCompose(identity -> feature.service().balance(feature.service().account(identity, currency.id())))
                .whenComplete((balance, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(sender, "economy.error." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    feature.send(sender, target == null ? "economy.balance.self" : "economy.balance.other", Map.of(
                            "player", balance.account().playerName(),
                            "currency", currency.display().plural(),
                            "balance", feature.service().format(currency.id(), balance.balance())
                    ));
                }));
        return 1;
    }

    private int setPayToggle(CommandSender sender, boolean enabled) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        feature.service().resolveIdentifier(player.getUniqueId().toString())
                .thenCompose(identity -> feature.service().setPaymentsEnabled(
                        feature.service().account(identity, currency.id()),
                        enabled,
                        identity.playerId(),
                        identity.playerName(),
                        "Player payment preference",
                        "player-command"
                ))
                .whenComplete((account, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.error." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    feature.send(player, account.paymentsEnabled()
                            ? "economy.paytoggle.enabled" : "economy.paytoggle.disabled");
                }));
        return 1;
    }

    private int showPayToggle(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        feature.service().resolveIdentifier(player.getUniqueId().toString())
                .thenCompose(identity -> feature.service().accountState(
                        feature.service().account(identity, currency.id())
                ))
                .whenComplete((account, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.error." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    feature.send(player, account.paymentsEnabled()
                            ? "economy.paytoggle.enabled" : "economy.paytoggle.disabled");
                }));
        return 1;
    }

    private int history(CommandSender sender, int page) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        feature.service().resolveIdentifier(player.getUniqueId().toString())
                .thenCompose(identity -> feature.service().history(
                        feature.service().account(identity, currency.id()), page, PAGE_SIZE
                ))
                .whenComplete((history, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.error." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    feature.send(player, "economy.history.header", Map.of("page", Integer.toString(page)));
                    if (history.entries().isEmpty()) {
                        feature.send(player, "economy.history.empty");
                        return;
                    }
                    for (HistoryItem item : history.entries()) {
                        feature.send(player, "economy.history.entry", Map.of(
                                "type", item.transactionType(),
                                "amount", feature.service().format(currency.id(), item.delta()),
                                "balance", feature.service().format(currency.id(), item.balanceAfter()),
                                "operation", item.operationId().toString()
                        ));
                    }
                }));
        return 1;
    }

    private int top(CommandSender sender, int page) {
        feature.service().top(currency.id(), page, PAGE_SIZE).whenComplete((entries, failure) ->
                feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(sender, "economy.error." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    feature.send(sender, "economy.top.header", Map.of("page", Integer.toString(page)));
                    if (entries.isEmpty()) {
                        feature.send(sender, "economy.top.empty");
                        return;
                    }
                    int rank = (page - 1) * PAGE_SIZE;
                    for (TopEntry entry : entries) {
                        rank++;
                        feature.send(sender, "economy.top.entry", Map.of(
                                "rank", Integer.toString(rank),
                                "player", entry.playerName(),
                                "balance", feature.service().format(currency.id(), entry.balance())
                        ));
                    }
                }));
        return 1;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        feature.send(sender, "economy.player_only");
        return null;
    }

    private boolean canUseAnyCommand(CommandSender sender) {
        return EconomyCommandPermissions.canUseAnyCurrencyCommand(sender, currency);
    }

    private boolean allowed(CommandSender sender, String action) {
        return EconomyCommandPermissions.playerAction(sender, currency.id(), action);
    }

    /** Shows only the enabled commands that the sender can execute for this currency. */
    private int help(CommandSender sender) {
        feature.send(sender, "economy.help.header", Map.of("command", "/" + name()));
        EconomySettings.Commands commands = currency.commands();
        if (commands.balance() && EconomyCommandPermissions.canViewOwnBalance(sender, currency.id())) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "[balance]", "description", "Bekijk je saldo"));
        }
        if (commands.balanceOthers() && EconomyCommandPermissions.canViewAnyBalance(sender, currency.id())) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "balance <speler>", "description", "Bekijk een saldo"));
        }
        if (commands.pay() && allowed(sender, "pay")) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "pay <speler> <bedrag>", "description", "Betaal een speler"));
            if (currency.payments().confirmationThreshold().signum() > 0) {
                feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "confirm", "description", "Bevestig een betaling"));
            }
        }
        if (commands.paytoggle() && allowed(sender, "paytoggle")) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "paytoggle <on|off|status>", "description", "Beheer inkomende betalingen"));
        }
        if (commands.history() && allowed(sender, "history")) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "history [pagina]", "description", "Bekijk je transacties"));
        }
        if (commands.top() && allowed(sender, "top")) {
            feature.send(sender, "economy.help.entry", Map.of("command", "/" + name(), "usage", "top [pagina]", "description", "Bekijk de ranglijst"));
        }
        return 1;
    }

}
