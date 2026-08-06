package nl.hauntedmc.serverfeatures.features.economy.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Audited administrator interface. */
public final class EconomyAdminCommand implements BrigadierCommand {
    private static final int PAGE_SIZE = 10;
    private final Economy feature;

    public EconomyAdminCommand(Economy feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "economy";
    }

    @Override
    public String description() {
        return "Inspect and administer Economy accounts.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name())
                .requires(source -> hasAnyAdminPermission(source.getSender()))
                .executes(context -> statusIfPermitted(context.getSource().getSender()));
        root.then(Commands.literal("status")
                .requires(source -> source.getSender().hasPermission(permission("status")))
                .executes(context -> status(context.getSource().getSender())));
        root.then(Commands.literal("currencies")
                .requires(source -> source.getSender().hasPermission(permission("status")))
                .executes(context -> currencies(context.getSource().getSender())));
        root.then(Commands.literal("balance")
                .requires(source -> source.getSender().hasPermission(permission("balance")))
                .then(playerCurrencyArguments(this::balance)));
        root.then(mutation("add", TransactionType.ADMIN_ADD));
        root.then(mutation("remove", TransactionType.ADMIN_REMOVE));
        root.then(mutation("set", TransactionType.ADMIN_SET));
        root.then(Commands.literal("payments")
                .requires(source -> source.getSender().hasPermission(permission("payments")))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(Commands.literal("on").executes(context -> payments(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "currency"),
                                        true
                                )))
                                .then(Commands.literal("off").executes(context -> payments(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "currency"),
                                        false
                                ))))));
        root.then(freeze("freeze", true));
        root.then(freeze("unfreeze", false));
        root.then(Commands.literal("history")
                .requires(source -> source.getSender().hasPermission(permission("history")))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .executes(context -> history(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "currency"),
                                        1
                                ))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1, 10_000))
                                        .executes(context -> history(
                                                context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "currency"),
                                                IntegerArgumentType.getInteger(context, "page")
                                        ))))));
        root.then(Commands.literal("verify")
                .requires(source -> source.getSender().hasPermission(permission("verify")))
                .executes(context -> verify(context.getSource().getSender())));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> mutation(String action, TransactionType type) {
        return Commands.literal(action)
                .requires(source -> source.getSender().hasPermission(permission(action)))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(context -> mutate(
                                                        context.getSource().getSender(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "currency"),
                                                        StringArgumentType.getString(context, "amount"),
                                                        StringArgumentType.getString(context, "reason"),
                                                        type
                                                ))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> freeze(String literal, boolean frozen) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(permission("freeze")))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> freeze(
                                                context.getSource().getSender(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "currency"),
                                                StringArgumentType.getString(context, "reason"),
                                                frozen
                                        )))));
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> playerCurrencyArguments(
            AdminAction action
    ) {
        return Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("currency", StringArgumentType.word())
                        .executes(context -> action.execute(
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "currency")
                        )));
    }

    private int statusIfPermitted(CommandSender sender) {
        if (!sender.hasPermission(permission("status"))) {
            feature.send(sender, "economy.error", Map.of("reason", "No permission"));
            return 0;
        }
        return status(sender);
    }

    private int status(CommandSender sender) {
        feature.send(sender, "economy.admin.status", Map.of(
                "server", feature.settings().serverKey(),
                "currencies", Integer.toString(feature.settings().currencies().size()),
                "vault", feature.vaultStatus(),
                "messaging", feature.messagingStatus()
        ));
        return 1;
    }

    private int currencies(CommandSender sender) {
        for (EconomySettings.Currency currency : feature.settings().currencies().values()) {
            feature.send(sender, "economy.admin.currency", Map.of(
                    "currency", currency.id(),
                    "scope", currency.scope().type().name(),
                    "scope_key", currency.scope().key(),
                    "command", "/" + currency.commands().root()
            ));
        }
        return 1;
    }

    private int balance(CommandSender sender, String target, String currencyId) {
        resolve(target, currencyId, (identity, currency) -> feature.service().balance(
                feature.service().account(identity, currency.id())
        ).whenComplete((balance, failure) -> feature.service().main(() -> {
            if (failure != null) {
                fail(sender, failure);
                return;
            }
            feature.send(sender, "economy.admin.balance", Map.of(
                    "player", identity.playerName(),
                    "currency", currency.id(),
                    "scope", currency.scope().key(),
                    "balance", feature.service().format(currency.id(), balance.balance())
            ));
        })), sender);
        return 1;
    }

    private int mutate(
            CommandSender sender,
            String target,
            String currencyId,
            String rawAmount,
            String reason,
            TransactionType type
    ) {
        if (reason == null || reason.isBlank()) {
            feature.send(sender, "economy.admin.reason_required");
            return 0;
        }
        resolve(target, currencyId, (identity, currency) -> {
            BigDecimal amount = parseAmount(rawAmount, currency, type == TransactionType.ADMIN_SET);
            Long actorId = sender instanceof Player player
                    ? feature.service().resolveSync(player).map(Identity::playerId).orElse(null)
                    : null;
            EconomyMutationRequest request = new EconomyMutationRequest(
                    "admin-command",
                    UUID.randomUUID().toString(),
                    feature.service().account(identity, currency.id()),
                    amount,
                    actorId,
                    sender.getName(),
                    reason,
                    Map.of("transaction_type", type.name())
            );
            feature.service().mutate(request, type, true).whenComplete((result, failure) ->
                    feature.service().main(() -> mutationResult(sender, identity, currency, result, failure)));
        }, sender);
        return 1;
    }

    private int payments(CommandSender sender, String target, String currencyId, boolean enabled) {
        resolve(target, currencyId, (identity, currency) -> {
            Long actorId = sender instanceof Player player
                    ? feature.service().resolveSync(player).map(Identity::playerId).orElse(null)
                    : null;
            feature.service().setPaymentsEnabled(
                    feature.service().account(identity, currency.id()),
                    enabled,
                    actorId,
                    sender.getName(),
                    "Administrator changed payment preference",
                    "admin-command"
            ).whenComplete((account, failure) -> feature.service().main(() -> {
                if (failure != null) {
                    fail(sender, failure);
                    return;
                }
                feature.send(sender, "economy.admin.payments", Map.of(
                        "player", identity.playerName(),
                        "state", enabled ? "on" : "off"
                ));
            }));
        }, sender);
        return 1;
    }

    private int freeze(CommandSender sender, String target, String currencyId, String reason, boolean frozen) {
        resolve(target, currencyId, (identity, currency) -> {
            Long actor = sender instanceof Player player
                    ? feature.service().resolveSync(player).map(Identity::playerId).orElse(null)
                    : null;
            feature.service().setFrozen(
                    feature.service().account(identity, currency.id()),
                    frozen,
                    actor,
                    sender.getName(),
                    reason
            ).whenComplete((account, failure) -> feature.service().main(() -> {
                if (failure != null) {
                    fail(sender, failure);
                    return;
                }
                feature.send(sender, frozen ? "economy.admin.frozen" : "economy.admin.unfrozen", Map.of(
                        "player", identity.playerName(),
                        "currency", currency.id()
                ));
            }));
        }, sender);
        return 1;
    }

    private int history(CommandSender sender, String target, String currencyId, int page) {
        resolve(target, currencyId, (identity, currency) -> feature.service().history(
                feature.service().account(identity, currency.id()), page, PAGE_SIZE
        ).whenComplete((history, failure) -> feature.service().main(() -> {
            if (failure != null) {
                fail(sender, failure);
                return;
            }
            feature.send(sender, "economy.history.header", Map.of("page", Integer.toString(page)));
            for (HistoryItem item : history.entries()) {
                feature.send(sender, "economy.history.entry", Map.of(
                        "type", item.transactionType(),
                        "amount", feature.service().format(currency.id(), item.delta()),
                        "balance", feature.service().format(currency.id(), item.balanceAfter()),
                        "operation", item.operationId().toString()
                ));
            }
        })), sender);
        return 1;
    }

    private int verify(CommandSender sender) {
        feature.service().verify().whenComplete((report, failure) -> feature.service().main(() -> {
            if (failure != null) {
                fail(sender, failure);
                return;
            }
            feature.send(sender, "economy.admin.verify", Map.of(
                    "health", report.healthy() ? "healthy" : "issues",
                    "accounts", Long.toString(report.accountCount()),
                    "transactions", Long.toString(report.transactionCount()),
                    "invalid", Long.toString(report.invalidBalanceCount()),
                    "invalid_entries", Long.toString(report.invalidEntryCount()),
                    "orphan_settings", Long.toString(report.orphanSettingsCount()),
                    "orphan_entries", Long.toString(report.orphanEntryCount()),
                    "identity_mismatches", Long.toString(report.identityMismatchCount()),
                    "accounts_without_entries", Long.toString(report.accountWithoutEntriesCount()),
                    "empty_transactions", Long.toString(report.transactionWithoutEntriesCount())
            ));
        }));
        return 1;
    }

    private void resolve(String target, String currencyId, ResolvedAction action, CommandSender sender) {
        EconomySettings.Currency currency;
        try {
            currency = feature.settings().requireCurrency(currencyId);
        } catch (RuntimeException exception) {
            feature.send(sender, "economy.error", Map.of("reason", exception.getMessage()));
            return;
        }
        feature.service().resolveIdentifier(target).whenComplete((identity, failure) -> {
            if (failure != null) {
                feature.service().main(() -> fail(sender, failure));
                return;
            }
            try {
                action.execute(identity, currency);
            } catch (RuntimeException exception) {
                feature.service().main(() -> fail(sender, exception));
            }
        });
    }

    private void mutationResult(
            CommandSender sender,
            Identity identity,
            EconomySettings.Currency currency,
            EconomyResult result,
            Throwable failure
    ) {
        if (failure != null) {
            fail(sender, failure);
            return;
        }
        if (!result.successful()) {
            feature.send(sender, "economy.error", Map.of("reason", result.message()));
            return;
        }
        feature.send(sender, "economy.admin.changed", Map.of(
                "player", identity.playerName(),
                "currency", currency.id(),
                "balance", feature.service().format(currency.id(), result.balance()),
                "operation", result.operationId() == null ? "-" : result.operationId().toString()
        ));
    }

    private BigDecimal parseAmount(String raw, EconomySettings.Currency currency, boolean setOperation) {
        String pattern = setOperation && currency.balances().allowNegative()
                ? "-?[0-9]+(?:\\.[0-9]{1,8})?"
                : "[0-9]+(?:\\.[0-9]{1,8})?";
        if (raw == null || !raw.matches(pattern)) {
            throw new IllegalArgumentException("Invalid amount");
        }
        BigDecimal amount = new BigDecimal(raw).setScale(
                currency.display().fractionalDigits(), currency.balances().rounding()
        );
        boolean invalid = setOperation
                ? !currency.balances().allowNegative() && amount.signum() < 0
                : amount.signum() <= 0;
        if (invalid) {
            String message = setOperation ? "Amount is outside the currency bounds" : "Amount must be positive";
            throw new IllegalArgumentException(message);
        }
        return amount;
    }

    private void fail(CommandSender sender, Throwable failure) {
        feature.send(sender, "economy.error", Map.of("reason", rootMessage(failure)));
    }

    private boolean hasAnyAdminPermission(CommandSender sender) {
        return sender.hasPermission(permission("status"))
                || sender.hasPermission(permission("balance"))
                || sender.hasPermission(permission("add"))
                || sender.hasPermission(permission("remove"))
                || sender.hasPermission(permission("set"))
                || sender.hasPermission(permission("payments"))
                || sender.hasPermission(permission("freeze"))
                || sender.hasPermission(permission("history"))
                || sender.hasPermission(permission("verify"));
    }

    private String permission(String action) {
        return "serverfeatures.feature.economy.admin." + action;
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

    @FunctionalInterface
    private interface AdminAction {
        int execute(CommandSender sender, String player, String currency);
    }

    @FunctionalInterface
    private interface ResolvedAction {
        void execute(Identity identity, EconomySettings.Currency currency);
    }
}
