package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.messaging.EconomyTransferMessage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Delivers recipient payment notifications only after verifying committed transfer data.
 *
 * <p>Redis messages are invalidations, not a source of monetary truth. The notification is
 * therefore built from the committed receipt and a fresh canonical balance read.</p>
 */
final class EconomyTransferNotifier {
    private static final long DEDUP_MILLIS = 10 * 60 * 1_000L;
    private static final int MAX_DEDUP_ENTRIES = 10_000;

    private final Economy feature;
    private final EconomyRepository repository;
    private final EconomyAccountCache cache;
    private final EconomyMainThreadExecutor mainThread;
    private final BiFunction<String, BigDecimal, String> formatter;
    private final ConcurrentHashMap<UUID, Long> notified = new ConcurrentHashMap<>();

    EconomyTransferNotifier(
            Economy feature,
            EconomyRepository repository,
            EconomyAccountCache cache,
            EconomyMainThreadExecutor mainThread,
            BiFunction<String, BigDecimal, String> formatter
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    /** Schedules local delivery for a transfer committed by this server. */
    void notifyLocal(
            UUID operationId,
            Identity sender,
            Identity recipient,
            EconomySettings.Currency currency,
            BigDecimal amount
    ) {
        mainThread.execute(() -> {
            if (Bukkit.getPlayer(recipient.playerUuid()) == null) {
                return;
            }
            cache.refresh(recipient, currency).whenComplete((account, failure) -> mainThread.execute(() -> {
                Player online = Bukkit.getPlayer(recipient.playerUuid());
                if (online == null) {
                    return;
                }
                if (failure != null) {
                    feature.getLogger().warning("Could not refresh local Economy transfer for "
                            + recipient.playerUuid() + ": " + EconomyFailure.rootMessage(failure));
                    return;
                }
                sendIfFirst(operationId, online, sender.playerName(), currency.id(), amount, account);
            }));
        });
    }

    /** Handles a remote notification after validating its shape and committed receipt. */
    void applyRemote(EconomyTransferMessage message, EconomySettings.Currency currency) {
        UUID operationId;
        UUID recipientUuid;
        try {
            operationId = UUID.fromString(message.getOperationId());
            recipientUuid = UUID.fromString(message.getRecipientPlayerUuid());
        } catch (RuntimeException ignored) {
            return;
        }
        mainThread.execute(() -> {
            if (Bukkit.getPlayer(recipientUuid) == null) {
                return;
            }
            repositoryLookup(operationId, recipientUuid, message.getRecipientPlayerId(), currency);
        });
    }

    void clear() {
        notified.clear();
    }

    private void repositoryLookup(
            UUID operationId,
            UUID recipientUuid,
            long recipientPlayerId,
            EconomySettings.Currency currency
    ) {
        feature.getLifecycleManager().getTaskManager().supplyAsync(() -> repository.transferReceipt(operationId))
                .thenCompose(optional -> {
                    TransferReceipt receipt = optional.orElseThrow(() -> new IllegalArgumentException(
                            "Unknown Economy transfer: " + operationId
                    ));
                    verifyReceipt(receipt, recipientUuid, recipientPlayerId, currency);
                    return cache.refreshFresh(recipientUuid, recipientPlayerId, currency)
                            .thenApply(account -> new VerifiedTransfer(receipt, account));
                }).whenComplete((verified, failure) -> mainThread.execute(() -> {
                    Player recipient = Bukkit.getPlayer(recipientUuid);
                    if (recipient == null) {
                        return;
                    }
                    if (failure != null) {
                        feature.getLogger().warning("Could not verify Economy transfer for " + recipientUuid
                                + ": " + EconomyFailure.rootMessage(failure));
                        return;
                    }
                    sendIfFirst(
                            operationId,
                            recipient,
                            verified.receipt().sender().playerName(),
                            currency.id(),
                            verified.receipt().amount(),
                            verified.account()
                    );
                }));
    }

    private static void verifyReceipt(
            TransferReceipt receipt,
            UUID recipientUuid,
            long recipientPlayerId,
            EconomySettings.Currency currency
    ) {
        if (receipt.recipient().playerId() != recipientPlayerId
                || !receipt.recipient().playerUuid().equals(recipientUuid)
                || !receipt.currencyId().equals(currency.id())
                || !receipt.scopeKey().equals(currency.scope().key())) {
            throw new IllegalArgumentException("Economy transfer notification does not match the committed transaction");
        }
    }

    private void sendIfFirst(
            UUID operationId,
            Player recipient,
            String senderName,
            String currencyId,
            BigDecimal amount,
            Account account
    ) {
        if (!markNotified(operationId)) {
            return;
        }
        feature.send(recipient, "economy.pay.received", Map.of(
                "player", senderName,
                "amount", formatter.apply(currencyId, amount),
                "balance", formatter.apply(currencyId, account.balance())
        ));
    }

    private boolean markNotified(UUID operationId) {
        long now = System.currentTimeMillis();
        if (notified.putIfAbsent(operationId, now) != null) {
            return false;
        }
        if (notified.size() > MAX_DEDUP_ENTRIES) {
            long cutoff = now - DEDUP_MILLIS;
            notified.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            int excess = notified.size() - MAX_DEDUP_ENTRIES;
            if (excess > 0) {
                notified.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(excess)
                        .map(Map.Entry::getKey).toList().forEach(notified::remove);
            }
        }
        return true;
    }

    private record VerifiedTransfer(TransferReceipt receipt, Account account) {
    }
}
