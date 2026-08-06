package nl.hauntedmc.serverfeatures.features.economy.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns Economy Redis publish/subscribe lifecycle. Messages never mutate balances. */
public final class EconomyMessaging {
    private static final long UNSUBSCRIBE_TIMEOUT_SECONDS = 5L;

    private final Economy feature;
    private final MessagingDataAccess messaging;
    private final String channel;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Subscription> subscriptions = new ArrayList<>();

    public EconomyMessaging(Economy feature, MessagingDataAccess messaging, String channel) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public synchronized void start() {
        if (closed.get()) {
            throw new IllegalStateException("Economy messaging is closed");
        }
        if (!subscriptions.isEmpty()) {
            return;
        }
        List<Subscription> created = new ArrayList<>();
        try {
            created.add(Objects.requireNonNull(
                    messaging.subscribe(
                            channel,
                            EconomyBalanceMessage.TYPE,
                            EconomyBalanceMessage.class,
                            message -> feature.service().applyRemoteBalance(message)
                    ),
                    "Redis balance subscription was not created"
            ));
            created.add(Objects.requireNonNull(
                    messaging.subscribe(
                            channel,
                            EconomyTransferMessage.TYPE,
                            EconomyTransferMessage.class,
                            message -> feature.service().applyRemoteTransfer(message)
                    ),
                    "Redis transfer subscription was not created"
            ));
            subscriptions.addAll(created);
        } catch (RuntimeException failure) {
            created.forEach(this::unsubscribe);
            throw failure;
        }
    }

    public void publish(String operationId, Account account) {
        if (closed.get() || account == null) {
            return;
        }
        EconomyBalanceMessage message = new EconomyBalanceMessage(
                feature.settings().serverKey(),
                operationId,
                account.identity().playerId(),
                account.identity().playerUuid().toString(),
                account.currencyId(),
                account.scopeKey(),
                account.version(),
                account.settingsVersion(),
                System.currentTimeMillis()
        );
        publishMessage(message, "balance update");
    }

    public void publishTransfer(
            String operationId,
            Identity recipient,
            String currencyId,
            String scopeKey
    ) {
        if (closed.get() || operationId == null || operationId.isBlank()) {
            return;
        }
        EconomyTransferMessage message = new EconomyTransferMessage(
                feature.settings().serverKey(),
                operationId,
                recipient.playerId(),
                recipient.playerUuid().toString(),
                currencyId,
                scopeKey,
                System.currentTimeMillis()
        );
        publishMessage(message, "transfer notification");
    }

    private void publishMessage(AbstractEventMessage message, String description) {
        try {
            CompletableFuture<Void> publication = messaging.publish(channel, message);
            if (publication == null) {
                feature.getLogger().warning("Economy messaging returned no future for " + description);
                return;
            }
            publication.exceptionally(failure -> {
                feature.getLogger().warning(
                        "Could not publish Economy " + description + ": " + rootMessage(failure)
                );
                return null;
            });
        } catch (RuntimeException failure) {
            // This is strictly post-commit fan-out. A Redis failure must never turn a
            // committed monetary transaction into an apparent failure for the caller.
            feature.getLogger().warning(
                    "Could not publish Economy " + description + ": " + rootMessage(failure)
            );
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<Subscription> current;
        synchronized (this) {
            current = List.copyOf(subscriptions);
            subscriptions.clear();
        }
        for (Subscription subscription : current) {
            unsubscribe(subscription);
        }
    }

    private void unsubscribe(Subscription subscription) {
        try {
            CompletableFuture<Void> future = subscription.unsubscribe();
            if (future != null) {
                future.orTimeout(UNSUBSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(failure -> {
                            feature.getLogger().warning(
                                    "Could not confirm Economy subscription shutdown: " + rootMessage(failure)
                            );
                            return null;
                        });
            }
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not close Economy subscription: " + rootMessage(exception));
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
