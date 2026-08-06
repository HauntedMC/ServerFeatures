package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns player payment validation, confirmation state, transfer submission, and sender feedback. */
final class CurrencyPaymentHandler {
    private static final long CONFIRMATION_TTL_MILLIS = 30_000L;

    private final Economy feature;
    private final EconomySettings.Currency currency;
    private final ConcurrentHashMap<UUID, PendingPayment> confirmations = new ConcurrentHashMap<>();

    CurrencyPaymentHandler(Economy feature, EconomySettings.Currency currency) {
        this.feature = feature;
        this.currency = currency;
    }

    int pay(CommandSender sender, String target, String rawAmount) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        BigDecimal amount;
        try {
            amount = parseAmount(rawAmount);
        } catch (IllegalArgumentException exception) {
            feature.send(player, "economy.invalid_amount", Map.of("reason", exception.getMessage()));
            return 0;
        }
        if (currency.payments().confirmationThreshold().signum() > 0
                && amount.compareTo(currency.payments().confirmationThreshold()) >= 0) {
            prepareConfirmation(player, target, amount);
        } else {
            execute(player, target, amount);
        }
        return 1;
    }

    int confirm(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return 0;
        PendingPayment pending = confirmations.remove(player.getUniqueId());
        if (pending == null || System.currentTimeMillis() - pending.createdAt() > CONFIRMATION_TTL_MILLIS) {
            feature.send(player, "economy.pay.no_confirmation");
            return 0;
        }
        execute(player, pending.recipient(), pending.amount());
        return 1;
    }

    private void prepareConfirmation(Player player, String target, BigDecimal amount) {
        feature.service().resolveIdentifier(target).whenComplete((recipient, failure) ->
                feature.service().main(() -> {
                    if (failure != null) {
                        feature.send(player, "economy.pay.failed", Map.of("reason", EconomyCommandSupport.rootMessage(failure)));
                        return;
                    }
                    confirmations.put(player.getUniqueId(), new PendingPayment(recipient, amount, System.currentTimeMillis()));
                    feature.send(player, "economy.pay.confirm", Map.of(
                            "player", recipient.playerName(),
                            "amount", feature.service().format(currency.id(), amount),
                            "command", "/" + currency.commands().root() + " confirm"));
                }));
    }

    private void execute(Player player, String target, BigDecimal amount) {
        feature.service().resolveIdentifier(target).whenComplete((recipient, failure) -> {
            if (failure != null) {
                feature.service().main(() -> feature.send(player, "economy.pay.failed",
                        Map.of("reason", EconomyCommandSupport.rootMessage(failure))));
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
                        feature.send(player, "economy.pay.failed", Map.of("reason", EconomyCommandSupport.rootMessage(failure)));
                        return;
                    }
                    if (!payment.result().successful()) {
                        feature.send(player, "economy.pay.failed", Map.of("reason", payment.result().message()));
                        return;
                    }
                    feature.send(player, "economy.pay.sent", Map.of(
                            "player", payment.recipient().playerName(),
                            "amount", feature.service().format(currency.id(), amount),
                            "balance", feature.service().format(currency.id(), payment.result().balance())));
                }));
    }

    private BigDecimal parseAmount(String raw) {
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

    private record PendingPayment(Identity recipient, BigDecimal amount, long createdAt) {
    }

    private record PaymentResult(Identity sender, Identity recipient, EconomyResult result) {
    }
}
