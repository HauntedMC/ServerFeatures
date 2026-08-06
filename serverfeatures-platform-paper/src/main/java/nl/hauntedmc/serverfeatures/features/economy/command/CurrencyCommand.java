package nl.hauntedmc.serverfeatures.features.economy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One dynamically registered player command root for a configured currency. */
public final class CurrencyCommand implements BrigadierCommand {
    private static final int PAGE_SIZE = 10;
    private static final long CONFIRMATION_TTL_MILLIS = 30_000L;

    private final Economy feature;
    private final EconomySettings.Currency currency;
    private final ConcurrentHashMap<UUID, Long> lastPayment = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PendingPayment> confirmations = new ConcurrentHashMap<>();

    public CurrencyCommand(Economy feature, EconomySettings.Currency currency) {
        this.feature = feature;
        this.currency = currency;
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
            root.executes(context -> allowed(context.getSource().getSender(), "balance")
                    ? balance(context.getSource().getSender(), null)
                    : 0);
            LiteralArgumentBuilder<CommandSourceStack> balance = Commands.literal("balance")
                    .requires(source -> allowed(source.getSender(), "balance"))
                    .executes(context -> balance(context.getSource().getSender(), null));
            if (currency.commands().balanceOthers()) {
                balance.then(Commands.argument("player", StringArgumentType.word())
                        .requires(source -> allowed(source.getSender(), "balance.others"))
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
                                    .executes(context -> pay(
                                            context.getSource().getSender(),
                                            StringArgumentType.getString(context, "player"),
                                            StringArgumentType.getString(context, "amount"),
                                            false
                                    )))));
            if (currency.payments().confirmationThreshold().signum() > 0) {
                root.then(Commands.literal("confirm")
                        .requires(source -> allowed(source.getSender(), "pay"))
                        .executes(context -> confirm(context.getSource().getSender())));
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
        return root.build();
    }

    private int balance(CommandSender sender, String target) {
        Player player = requirePlayer(sender);
        if (player == null && target == null) {
            return 0;
        }
        String identifier = target == null ? player.getUniqueId().toString() : target;
        feature.service().resolveIdentifier(identifier)
                .thenCompose(identity -> feature.service().balance(feature.service().account(identity, currency.id())))
                .whenComplete((balance, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(sender, "economy.error", Map.of("reason", rootMessage(failure)));
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

    private int pay(CommandSender sender, String target, String rawAmount, boolean confirmed) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        BigDecimal amount;
        try {
            amount = parseAmount(rawAmount);
        } catch (IllegalArgumentException exception) {
            feature.send(player, "economy.invalid_amount", Map.of("reason", exception.getMessage()));
            return 0;
        }
        long now = System.currentTimeMillis();
        long cooldown = currency.payments().cooldown().toMillis();
        Long previous = lastPayment.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            feature.send(player, "economy.pay.cooldown", Map.of(
                    "seconds", Long.toString(Math.max(1L, (cooldown - (now - previous) + 999L) / 1000L))
            ));
            return 0;
        }
        if (!confirmed && currency.payments().confirmationThreshold().signum() > 0
                && amount.compareTo(currency.payments().confirmationThreshold()) >= 0) {
            confirmations.put(player.getUniqueId(), new PendingPayment(target, amount, now));
            feature.send(player, "economy.pay.confirm", Map.of(
                    "player", target,
                    "amount", feature.service().format(currency.id(), amount),
                    "command", "/" + name() + " confirm"
            ));
            return 1;
        }
        executePayment(player, target, amount);
        return 1;
    }

    private int confirm(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        PendingPayment pending = confirmations.remove(player.getUniqueId());
        if (pending == null || System.currentTimeMillis() - pending.createdAt() > CONFIRMATION_TTL_MILLIS) {
            feature.send(player, "economy.pay.no_confirmation");
            return 0;
        }
        return pay(player, pending.target(), pending.amount().toPlainString(), true);
    }

    private void executePayment(Player player, String target, BigDecimal amount) {
        feature.service().resolveIdentifier(player.getUniqueId().toString()).thenCombine(
                feature.service().resolveIdentifier(target),
                ResolvedPayment::new
        ).thenCompose(resolved -> {
            if (!currency.payments().allowOfflineRecipient()
                    && feature.getPlugin().getServer().getPlayer(resolved.recipient().playerUuid()) == null) {
                throw new IllegalArgumentException("Recipient must be online");
            }
            return feature.service().transfer(new EconomyTransferRequest(
                    "player-command",
                    UUID.randomUUID().toString(),
                    feature.service().account(resolved.sender(), currency.id()),
                    feature.service().account(resolved.recipient(), currency.id()),
                    amount,
                    resolved.sender().playerId(),
                    resolved.sender().playerName(),
                    "Player payment",
                    Map.of("command", name()),
                    false
            )).thenApply(result -> new PaymentResult(resolved, result));
        }).whenComplete((payment, failure) -> feature.service().main(() -> {
            if (failure != null) {
                feature.send(player, "economy.pay.failed", Map.of("reason", rootMessage(failure)));
                return;
            }
            EconomyResult result = payment.result();
            if (!result.successful()) {
                feature.send(player, "economy.pay.failed", Map.of("reason", result.message()));
                return;
            }
            lastPayment.put(player.getUniqueId(), System.currentTimeMillis());
            String formatted = feature.service().format(currency.id(), amount);
            feature.send(player, "economy.pay.sent", Map.of(
                    "player", payment.resolved().recipient().playerName(),
                    "amount", formatted,
                    "balance", feature.service().format(currency.id(), result.balance())
            ));
            Player recipient = feature.getPlugin().getServer().getPlayer(payment.resolved().recipient().playerUuid());
            if (recipient != null) {
                feature.send(recipient, "economy.pay.received", Map.of(
                        "player", payment.resolved().sender().playerName(),
                        "amount", formatted,
                        "balance", feature.service().format(currency.id(), result.counterpartBalance())
                ));
            }
        }));
    }

    private int setPayToggle(CommandSender sender, boolean enabled) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        feature.service().resolveIdentifier(player.getUniqueId().toString())
                .thenCompose(identity -> feature.service().setPaymentsEnabled(
                        feature.service().account(identity, currency.id()), enabled
                ))
                .whenComplete((account, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.error", Map.of("reason", rootMessage(failure)));
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
        boolean enabled = feature.service().cachedAccount(player.getUniqueId(), currency.id())
                .map(account -> account.paymentsEnabled())
                .orElse(currency.payments().defaultEnabled());
        feature.send(player, enabled ? "economy.paytoggle.enabled" : "economy.paytoggle.disabled");
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
                        feature.send(player, "economy.error", Map.of("reason", rootMessage(failure)));
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
                        feature.send(sender, "economy.error", Map.of("reason", rootMessage(failure)));
                        return;
                    }
                    feature.send(sender, "economy.top.header", Map.of("page", Integer.toString(page)));
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

    private BigDecimal parseAmount(String raw) {
        if (raw == null || !raw.matches("[0-9]+(?:\\.[0-9]{1,8})?")) {
            throw new IllegalArgumentException("Use a positive decimal amount");
        }
        BigDecimal amount = new BigDecimal(raw).setScale(
                currency.display().fractionalDigits(), currency.balances().rounding()
        );
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return amount;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        feature.send(sender, "economy.player_only");
        return null;
    }

    private boolean canUseAnyCommand(CommandSender sender) {
        EconomySettings.Commands commands = currency.commands();
        return commands.balance() && allowed(sender, "balance")
                || commands.pay() && allowed(sender, "pay")
                || commands.paytoggle() && allowed(sender, "paytoggle")
                || commands.history() && allowed(sender, "history")
                || commands.top() && allowed(sender, "top");
    }

    private boolean allowed(CommandSender sender, String action) {
        return sender.hasPermission(permission(action))
                || sender.hasPermission("serverfeatures.feature.economy." + action);
    }

    private String permission(String action) {
        return "serverfeatures.feature.economy.currency." + currency.id() + "." + action;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record PendingPayment(String target, BigDecimal amount, long createdAt) {
    }

    private record ResolvedPayment(Identity sender, Identity recipient) {
    }

    private record PaymentResult(ResolvedPayment resolved, EconomyResult result) {
    }
}
