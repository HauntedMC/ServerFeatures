package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Validates player payments, delegates confirmation lifetime to a tracker, and submits transfers. */
final class CurrencyPaymentHandler {
    private final Economy feature;
    private final EconomySettings.Currency currency;
    private final PaymentConfirmationTracker confirmations = new PaymentConfirmationTracker();

    CurrencyPaymentHandler(Economy feature, EconomySettings.Currency currency) {
        this.feature = feature;
        this.currency = currency;
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                confirmations::pruneExpired, BukkitTime.seconds(30L));
    }

    int pay(CommandSender sender, String target, String rawAmount) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        BigDecimal amount;
        try {
            amount = parseAmount(rawAmount);
        } catch (IllegalArgumentException exception) {
            feature.send(player, "economy.invalid_amount");
            return 0;
        }
        UUID confirmationToken = confirmations.begin(player.getUniqueId());
        if (currency.payments().confirmationThreshold().signum() > 0
                && amount.compareTo(currency.payments().confirmationThreshold()) >= 0) {
            prepareConfirmation(player, target, amount, confirmationToken);
        } else {
            execute(player, target, amount);
        }
        return 1;
    }

    int confirm(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        PaymentConfirmationTracker.PendingPayment pending = confirmations.consume(player.getUniqueId()).orElse(null);
        if (pending == null) {
            feature.send(player, "economy.pay.no_confirmation");
            return 0;
        }
        execute(player, pending.recipient(), pending.amount());
        return 1;
    }

    /** Resolves the recipient before showing a confirmation, guarded by the attempt token. */
    private void prepareConfirmation(Player player, String target, BigDecimal amount, UUID confirmationToken) {
        feature.service().resolveIdentifier(target).whenComplete((recipient, failure) ->
                feature.service().main(() -> {
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        feature.send(player, "economy.pay.failed." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    if (!confirmations.confirm(player.getUniqueId(), confirmationToken, recipient, amount)) return;
                    feature.send(player, "economy.pay.confirm", Map.of(
                            "player", recipient.playerName(),
                            "amount", feature.service().format(currency.id(), amount),
                            "command", "/" + currency.commands().root() + " confirm"));
                }));
    }

    private void execute(Player player, String target, BigDecimal amount) {
        feature.service().resolveIdentifier(target).whenComplete((recipient, failure) -> {
            if (failure != null) {
                feature.service().main(() -> feature.send(player,
                        "economy.pay.failed." + EconomyCommandSupport.failureMessageKey(failure)));
                return;
            }
            execute(player, recipient, amount);
        });
    }

    private void execute(Player player, Identity recipient, BigDecimal amount) {
        feature.service().resolveIdentifier(player.getUniqueId().toString()).thenCompose(sender ->
                feature.service().transfer(new EconomyTransferRequest(
                        "player-command", UUID.randomUUID().toString(),
                        feature.service().account(sender, currency.id()),
                        feature.service().account(recipient, currency.id()), amount,
                        sender.playerId(), sender.playerName(), "Player payment",
                        Map.of("command", currency.commands().root()), false))
                        .thenApply(result -> new PaymentResult(sender, recipient, result)))
                .whenComplete((payment, failure) -> feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.pay.failed." + EconomyCommandSupport.failureMessageKey(failure));
                        return;
                    }
                    if (payment == null || !payment.result().successful()) {
                        feature.send(player, "economy.pay.failed." + EconomyCommandSupport.resultMessageKey(
                                payment == null ? null : payment.result()));
                        return;
                    }
                    feature.send(player, "economy.pay.sent", Map.of(
                            "player", payment.recipient().playerName(),
                            "amount", feature.service().format(currency.id(), amount),
                            "balance", feature.service().format(currency.id(), payment.result().balance())));
                }));
    }

    private BigDecimal parseAmount(String raw) {
        EconomyCommandSupport.requireSupportedAmountLength(raw, false);
        if (raw == null || !raw.matches("[0-9]+(?:\\.[0-9]{1,8})?")) {
            throw new IllegalArgumentException("Use a positive decimal amount");
        }
        BigDecimal amount = new BigDecimal(raw).setScale(
                currency.display().fractionalDigits(), currency.balances().rounding());
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        return amount;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        feature.send(sender, "economy.player_only");
        return null;
    }

    private record PaymentResult(Identity sender, Identity recipient, EconomyResult result) {
    }
}
