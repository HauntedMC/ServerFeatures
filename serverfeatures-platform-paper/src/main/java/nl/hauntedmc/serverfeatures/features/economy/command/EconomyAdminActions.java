package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Executes audited administrator use cases and renders their localized command feedback. */
final class EconomyAdminActions {
    private static final int PAGE_SIZE = 10;
    private final Economy feature;

    EconomyAdminActions(Economy feature) {
        this.feature = feature;
    }

    int statusIfPermitted(CommandSender sender) {
        return EconomyCommandPermissions.adminAction(sender, "status") ? status(sender) : help(sender);
    }

    /** Lists the administrative operations that are available to the current sender. */
    int help(CommandSender sender) {
        feature.send(sender, "economy.admin.help.header");
        sendHelp(sender, "status", "status", "Bekijk de Economy-status");
        sendHelp(sender, "status", "currencies", "Bekijk geconfigureerde currencies");
        sendHelp(sender, "balance", "balance <speler> <currency>", "Bekijk een saldo");
        sendHelp(sender, "balance", "account <speler> <currency>", "Bekijk saldo en accountstatus");
        sendHelp(sender, "add", "add <speler> <currency> <bedrag> <reden>", "Voeg saldo toe");
        sendHelp(sender, "remove", "remove <speler> <currency> <bedrag> <reden>", "Verwijder saldo");
        sendHelp(sender, "set", "set <speler> <currency> <bedrag> <reden>", "Stel een saldo in");
        sendHelp(sender, "payments", "payments <speler> <currency> <on|off> <reden>", "Beheer inkomende betalingen");
        sendHelp(sender, "freeze", "freeze <speler> <currency> <reden>", "Bevries een account");
        sendHelp(sender, "freeze", "unfreeze <speler> <currency> <reden>", "Geef een account vrij");
        sendHelp(sender, "history", "history <speler> <currency> [pagina]", "Bekijk transacties");
        sendHelp(sender, "verify", "verify", "Controleer de journaalintegriteit");
        return 1;
    }

    int status(CommandSender sender) {
        feature.send(sender, "economy.admin.status", Map.of(
                "server", feature.settings().serverKey(),
                "currencies", Integer.toString(feature.settings().currencies().size()),
                "vault", feature.vaultStatus(), "messaging", feature.messagingStatus()));
        return 1;
    }

    int currencies(CommandSender sender) {
        for (EconomySettings.Currency currency : feature.settings().currencies().values()) {
            feature.send(sender, "economy.admin.currency", Map.of(
                    "currency", currency.id(), "scope", currency.scope().type().name(),
                    "scope_key", currency.scope().key(), "command", "/" + currency.commands().root()));
        }
        return 1;
    }

    int balance(CommandSender sender, String target, String currencyId) {
        resolve(target, currencyId, (identity, currency) -> feature.service().balance(
                feature.service().account(identity, currency.id())).whenComplete((balance, failure) ->
                feature.service().main(() -> {
                    if (failure != null) { fail(sender, failure); return; }
                    feature.send(sender, "economy.admin.balance", Map.of(
                            "player", identity.playerName(), "currency", currency.id(),
                            "scope", currency.scope().key(),
                            "balance", feature.service().format(currency.id(), balance.balance())));
                })), sender);
        return 1;
    }

    /** Shows the account state that explains whether normal player payments can reach this account. */
    int account(CommandSender sender, String target, String currencyId) {
        resolve(target, currencyId, (identity, currency) -> feature.service().accountState(
                feature.service().account(identity, currency.id())).whenComplete((account, failure) ->
                feature.service().main(() -> accountResult(sender, account, failure))), sender);
        return 1;
    }

    int mutate(CommandSender sender, String target, String currencyId, String rawAmount,
               String reason, TransactionType type) {
        if (reason == null || reason.isBlank()) {
            feature.send(sender, "economy.admin.reason_required");
            return 0;
        }
        Actor actor = actor(sender);
        resolve(target, currencyId, (identity, currency) -> {
            BigDecimal amount = parseAmount(rawAmount, currency, type == TransactionType.SET);
            EconomyMutationRequest request = new EconomyMutationRequest(
                    "admin-command", UUID.randomUUID().toString(), feature.service().account(identity, currency.id()),
                    amount, actor.playerId(), actor.name(), reason, Map.of("command", "economy " + type.name().toLowerCase()));
            feature.service().mutate(request, type, true).whenComplete((result, failure) ->
                    feature.service().main(() -> mutationResult(sender, identity, currency, result, failure)));
        }, sender);
        return 1;
    }

    int payments(CommandSender sender, String target, String currencyId, boolean enabled, String reason) {
        if (reason == null || reason.isBlank()) {
            feature.send(sender, "economy.admin.reason_required");
            return 0;
        }
        Actor actor = actor(sender);
        resolve(target, currencyId, (identity, currency) -> feature.service().setPaymentsEnabled(
                feature.service().account(identity, currency.id()), enabled, actor.playerId(), actor.name(), reason, "admin-command")
                .whenComplete((account, failure) -> feature.service().main(() -> {
                    if (failure != null) { fail(sender, failure); return; }
                    feature.send(sender, "economy.admin.payments", Map.of(
                            "player", identity.playerName(), "state", enabled ? "on" : "off"));
                })), sender);
        return 1;
    }

    int freeze(CommandSender sender, String target, String currencyId, String reason, boolean frozen) {
        if (reason == null || reason.isBlank()) {
            feature.send(sender, "economy.admin.reason_required");
            return 0;
        }
        Actor actor = actor(sender);
        resolve(target, currencyId, (identity, currency) -> feature.service().setFrozen(
                feature.service().account(identity, currency.id()), frozen, actor.playerId(), actor.name(), reason)
                .whenComplete((account, failure) -> feature.service().main(() -> {
                    if (failure != null) { fail(sender, failure); return; }
                    feature.send(sender, frozen ? "economy.admin.frozen" : "economy.admin.unfrozen",
                            Map.of("player", identity.playerName(), "currency", currency.id()));
                })), sender);
        return 1;
    }

    int history(CommandSender sender, String target, String currencyId, int page) {
        resolve(target, currencyId, (identity, currency) -> feature.service().history(
                feature.service().account(identity, currency.id()), page, PAGE_SIZE)
                .whenComplete((history, failure) -> feature.service().main(() -> {
                    if (failure != null) { fail(sender, failure); return; }
                    feature.send(sender, "economy.history.header", Map.of("page", Integer.toString(page)));
                    if (history.entries().isEmpty()) {
                        feature.send(sender, "economy.history.empty");
                        return;
                    }
                    for (HistoryItem item : history.entries()) {
                        feature.send(sender, "economy.history.entry", Map.of(
                                "type", item.transactionType(),
                                "amount", feature.service().format(currency.id(), item.delta()),
                                "balance", feature.service().format(currency.id(), item.balanceAfter()),
                                "operation", item.operationId().toString()));
                    }
                })), sender);
        return 1;
    }

    int verify(CommandSender sender) {
        feature.service().verify().whenComplete((report, failure) -> feature.service().main(() -> {
            if (failure != null) { fail(sender, failure); return; }
            feature.send(sender, "economy.admin.verify", Map.ofEntries(
                    Map.entry("health", report.healthy() ? "healthy" : "issues"),
                    Map.entry("accounts", Long.toString(report.accountCount())),
                    Map.entry("transactions", Long.toString(report.transactionCount())),
                    Map.entry("invalid", Long.toString(report.invalidBalanceCount())),
                    Map.entry("invalid_entries", Long.toString(report.invalidEntryCount())),
                    Map.entry("invalid_transactions", Long.toString(report.invalidTransactionCount())),
                    Map.entry("orphan_settings", Long.toString(report.orphanSettingsCount())),
                    Map.entry("orphan_entries", Long.toString(report.orphanEntryCount())),
                    Map.entry("identity_mismatches", Long.toString(report.identityMismatchCount())),
                    Map.entry("entry_account_mismatches", Long.toString(report.entryAccountMismatchCount())),
                    Map.entry("accounts_without_entries", Long.toString(report.accountWithoutEntriesCount())),
                    Map.entry("empty_transactions", Long.toString(report.transactionWithoutEntriesCount())),
                    Map.entry("balance_journal_mismatches", Long.toString(report.balanceJournalMismatchCount())),
                    Map.entry("continuity_errors", Long.toString(report.journalContinuityErrorCount()))));
        }));
        return 1;
    }

    private Actor actor(CommandSender sender) {
        return sender instanceof Player player
                ? new Actor(feature.service().activeIdentity(player).map(Identity::playerId).orElse(null), sender.getName())
                : new Actor(null, sender.getName());
    }

    private void accountResult(CommandSender sender, Account account, Throwable failure) {
        if (failure != null) {
            fail(sender, failure);
            return;
        }
        feature.send(sender, "economy.admin.account", Map.of(
                "player", account.identity().playerName(), "currency", account.currencyId(), "scope", account.scopeKey(),
                "balance", feature.service().format(account.currencyId(), account.balance()),
                "payments", account.paymentsEnabled() ? "on" : "off", "status", account.status().name().toLowerCase()));
    }

    private void sendHelp(CommandSender sender, String permission, String usage, String description) {
        if (EconomyCommandPermissions.adminAction(sender, permission)) {
            feature.send(sender, "economy.admin.help.entry", Map.of("usage", usage, "description", description));
        }
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

    private void mutationResult(CommandSender sender, Identity identity, EconomySettings.Currency currency,
                                EconomyResult result, Throwable failure) {
        if (failure != null) { fail(sender, failure); return; }
        if (!result.successful()) {
            feature.send(sender, "economy.error", Map.of("reason", EconomyCommandSupport.resultMessage(result)));
            return;
        }
        feature.send(sender, "economy.admin.changed", Map.of(
                "player", identity.playerName(), "currency", currency.id(),
                "balance", feature.service().format(currency.id(), result.balance()),
                "operation", result.operationId() == null ? "-" : result.operationId().toString()));
    }

    private BigDecimal parseAmount(String raw, EconomySettings.Currency currency, boolean setOperation) {
        EconomyCommandSupport.requireSupportedAmountLength(raw, setOperation && currency.balances().allowNegative());
        String pattern = setOperation && currency.balances().allowNegative()
                ? "-?[0-9]+(?:\\.[0-9]{1,8})?" : "[0-9]+(?:\\.[0-9]{1,8})?";
        if (raw == null || !raw.matches(pattern)) throw new IllegalArgumentException("Invalid amount");
        BigDecimal amount = new BigDecimal(raw).setScale(
                currency.display().fractionalDigits(), currency.balances().rounding());
        boolean invalid = setOperation ? !currency.balances().allowNegative() && amount.signum() < 0 : amount.signum() <= 0;
        if (invalid) throw new IllegalArgumentException(
                setOperation ? "Amount is outside the currency bounds" : "Amount must be positive");
        return amount;
    }

    private void fail(CommandSender sender, Throwable failure) {
        feature.send(sender, "economy.error", Map.of("reason", EconomyCommandSupport.rootMessage(failure)));
    }

    @FunctionalInterface
    private interface ResolvedAction {
        void execute(Identity identity, EconomySettings.Currency currency);
    }

    private record Actor(Long playerId, String name) {
    }
}
