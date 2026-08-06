from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}: {old[:80]!r}")
    write(path, content.replace(old, new, 1))


def replace_all(path: str, old: str, new: str, minimum: int = 1) -> None:
    content = read(path)
    count = content.count(old)
    if count < minimum:
        raise RuntimeError(f"Expected at least {minimum} matches in {path}, found {count}: {old!r}")
    write(path, content.replace(old, new))


repository = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/persistence/EconomyRepository.java"
replace_once(
    repository,
    """import java.security.NoSuchAlgorithmException;\nimport java.time.Instant;""",
    """import java.security.NoSuchAlgorithmException;\nimport java.sql.SQLException;\nimport java.sql.SQLRecoverableException;\nimport java.sql.SQLTransientException;\nimport java.time.Instant;""",
)
replace_once(
    repository,
    """import java.util.UUID;\nimport java.util.function.Supplier;""",
    """import java.util.UUID;\nimport java.util.concurrent.ThreadLocalRandom;\nimport java.util.function.Supplier;""",
)
replace_once(
    repository,
    """    public boolean accountExists(Identity identity, EconomySettings.Currency currency) {\n        Objects.requireNonNull(identity, \"identity\");\n        Objects.requireNonNull(currency, \"currency\");\n        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());\n        return executeWithRetry(() -> orm.runInTransaction(session -> {\n            ensurePlayerIdentity(session, identity, System.currentTimeMillis(), false);\n            return session.find(EconomyBalanceEntity.class, id) != null;\n        }));\n    }\n\n\n""",
    """    public boolean accountExists(Identity identity, EconomySettings.Currency currency) {\n        Objects.requireNonNull(identity, \"identity\");\n        Objects.requireNonNull(currency, \"currency\");\n        String id = accountId(identity.playerId(), currency.id(), currency.scope().key());\n        return executeWithRetry(() -> orm.runInTransaction(session -> {\n            EconomyPlayerIdentityEntity canonical = session.find(\n                    EconomyPlayerIdentityEntity.class,\n                    identity.playerId()\n            );\n            EconomyPlayerIdentityEntity uuidOwner = findIdentityByUuid(session, identity.playerUuid());\n            if (canonical == null) {\n                if (uuidOwner != null && uuidOwner.getPlayerId() != identity.playerId()) {\n                    throw new IllegalStateException(\n                            \"Economy UUID \" + identity.playerUuid() + \" is already owned by player ID \"\n                                    + uuidOwner.getPlayerId()\n                    );\n                }\n                return false;\n            }\n            if (!Objects.equals(canonical.getPlayerUuid(), identity.playerUuid().toString())) {\n                throw new IllegalStateException(\n                        \"Economy player ID \" + identity.playerId() + \" is already owned by UUID \"\n                                + canonical.getPlayerUuid()\n                );\n            }\n            return session.find(EconomyBalanceEntity.class, id) != null;\n        }));\n    }\n\n    public Optional<Identity> identityByUuid(UUID playerUuid) {\n        Objects.requireNonNull(playerUuid, \"playerUuid\");\n        return executeWithRetry(() -> orm.runInTransaction(session -> {\n            EconomyPlayerIdentityEntity entity = findIdentityByUuid(session, playerUuid);\n            if (entity == null) {\n                return Optional.empty();\n            }\n            return Optional.of(new Identity(\n                    entity.getPlayerId(),\n                    UUID.fromString(entity.getPlayerUuid()),\n                    entity.getPlayerName()\n            ));\n        }));\n    }\n\n    private static EconomyPlayerIdentityEntity findIdentityByUuid(Session session, UUID playerUuid) {\n        return session.createSelectionQuery(\n                        \"from EconomyPlayerIdentityEntity where playerUuid = :uuid\",\n                        EconomyPlayerIdentityEntity.class\n                )\n                .setParameter(\"uuid\", playerUuid.toString())\n                .setMaxResults(1)\n                .getResultStream()\n                .findFirst()\n                .orElse(null);\n    }\n\n\n""",
)
replace_once(
    repository,
    """    private <T> T executeWithRetry(Supplier<T> work) {\n        RuntimeException last = null;\n        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {\n            int attemptNumber = attempt + 1;\n            try {\n                return work.get();\n            } catch (EconomyRejectedException rejected) {\n                throw rejected;\n            } catch (RuntimeException failure) {\n                last = failure;\n                if (!isTransient(failure) || attemptNumber == MAX_RETRIES) {\n                    throw failure;\n                }\n                try {\n                    Thread.sleep(5L * attemptNumber);\n                } catch (InterruptedException interrupted) {\n                    Thread.currentThread().interrupt();\n                    throw failure;\n                }\n            }\n        }\n        throw last == null ? new IllegalStateException(\"Economy operation did not execute\") : last;\n    }\n\n    private static boolean isTransient(Throwable failure) {\n        Throwable current = failure;\n        while (current != null) {\n            String message = current.getMessage();\n            if (message != null) {\n                String normalized = message.toLowerCase(java.util.Locale.ROOT);\n                if (normalized.contains(\"deadlock\")\n                        || normalized.contains(\"lock wait timeout\")\n                        || normalized.contains(\"duplicate entry\")\n                        || normalized.contains(\"constraint\") && normalized.contains(\"idempotency\")) {\n                    return true;\n                }\n            }\n            current = current.getCause();\n        }\n        return false;\n    }\n""",
    """    private <T> T executeWithRetry(Supplier<T> work) {\n        RuntimeException last = null;\n        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {\n            int attemptNumber = attempt + 1;\n            try {\n                return work.get();\n            } catch (EconomyRejectedException rejected) {\n                throw rejected;\n            } catch (RuntimeException failure) {\n                last = failure;\n                if (!isTransient(failure) || attemptNumber == MAX_RETRIES) {\n                    throw failure;\n                }\n                long baseDelayMillis = 5L << attempt;\n                long jitterMillis = ThreadLocalRandom.current().nextLong(baseDelayMillis + 1L);\n                try {\n                    Thread.sleep(baseDelayMillis + jitterMillis);\n                } catch (InterruptedException interrupted) {\n                    Thread.currentThread().interrupt();\n                    throw failure;\n                }\n            }\n        }\n        throw last == null ? new IllegalStateException(\"Economy operation did not execute\") : last;\n    }\n\n    static boolean isTransient(Throwable failure) {\n        Throwable current = failure;\n        while (current != null) {\n            if (current instanceof SQLTransientException || current instanceof SQLRecoverableException) {\n                return true;\n            }\n            if (current instanceof SQLException sqlException) {\n                String sqlState = sqlException.getSQLState();\n                int errorCode = sqlException.getErrorCode();\n                if (sqlState != null && (sqlState.startsWith(\"08\") || sqlState.startsWith(\"40\"))\n                        || errorCode == 1062\n                        || errorCode == 1205\n                        || errorCode == 1213) {\n                    return true;\n                }\n            }\n            String className = current.getClass().getName();\n            if (className.endsWith(\"JDBCConnectionException\")\n                    || className.endsWith(\"LockAcquisitionException\")\n                    || className.endsWith(\"PessimisticLockException\")\n                    || className.endsWith(\"OptimisticLockException\")) {\n                return true;\n            }\n            String message = current.getMessage();\n            if (message != null) {\n                String normalized = message.toLowerCase(java.util.Locale.ROOT);\n                if (normalized.contains(\"deadlock\")\n                        || normalized.contains(\"lock wait timeout\")\n                        || normalized.contains(\"duplicate entry\")\n                        || normalized.contains(\"constraint\") && normalized.contains(\"idempotency\")) {\n                    return true;\n                }\n            }\n            current = current.getCause();\n        }\n        return false;\n    }\n""",
)

service = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/service/EconomyService.java"
replace_once(
    service,
    """        // DataRegistry remains canonical even for an already existing Economy account.\n        // A registry outage must fail closed rather than authorizing a stale identity mapping.\n        return awaitIdentity(identityResolver.findByUuid(playerUuid), playerUuid.toString());\n""",
    """        // The durable UUID binding is immutable and safe to reuse for an existing Economy identity.\n        // This avoids blocking common synchronous Vault calls on a remote registry lookup.\n        Optional<Identity> persisted = repository.identityByUuid(playerUuid);\n        if (persisted.isPresent()) {\n            return persisted;\n        }\n        // DataRegistry remains canonical before an Economy identity exists.\n        return awaitIdentity(identityResolver.findByUuid(playerUuid), playerUuid.toString());\n""",
)
replace_once(
    service,
    """        if (outcome != null\n                && outcome.successful()\n                && outcome.operationId() != null\n""",
    """        if (outcome != null\n                && outcome.status() == EconomyResultStatus.SUCCESS\n                && outcome.operationId() != null\n""",
)
replace_once(
    service,
    """    private EconomyResult failureResult(Throwable failure) {\n""",
    """    public String userFacingFailure(Throwable failure) {\n        Throwable root = unwrap(failure);\n        if (root instanceof EconomyRejectedException\n                || root instanceof UnknownPlayerException\n                || root instanceof UnknownCurrencyException\n                || root instanceof IllegalArgumentException) {\n            return rootMessage(root);\n        }\n        return \"De economie is tijdelijk niet beschikbaar. Probeer het later opnieuw.\";\n    }\n\n    private EconomyResult failureResult(Throwable failure) {\n""",
)
replace_once(
    service,
    """        feature.getLogger().warning(\"Economy operation failed: \" + rootMessage(root));\n        return new EconomyResult(EconomyResultStatus.TEMPORARY_FAILURE, null, null, null, rootMessage(root));\n""",
    """        feature.getLogger().log(java.util.logging.Level.WARNING, \"Economy operation failed\", root);\n        return new EconomyResult(\n                EconomyResultStatus.TEMPORARY_FAILURE,\n                null,\n                null,\n                null,\n                \"Economy is temporarily unavailable\"\n        );\n""",
)

currency_command = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/command/CurrencyCommand.java"
replace_all(
    currency_command,
    "rootMessage(failure)",
    "feature.service().userFacingFailure(failure)",
)
replace_once(
    currency_command,
    """                    confirmations.put(\n                            player.getUniqueId(),\n                            new PendingPayment(recipient, amount, System.currentTimeMillis())\n                    );\n""",
    """                    if (!player.isOnline()) {\n                        return;\n                    }\n                    purgeExpiredConfirmations();\n                    confirmations.put(\n                            player.getUniqueId(),\n                            new PendingPayment(recipient, amount, System.currentTimeMillis())\n                    );\n""",
)
replace_once(
    currency_command,
    """    private Player requirePlayer(CommandSender sender) {\n""",
    """    public void evict(UUID playerUuid) {\n        if (playerUuid != null) {\n            confirmations.remove(playerUuid);\n        }\n    }\n\n    public void close() {\n        confirmations.clear();\n    }\n\n    private void purgeExpiredConfirmations() {\n        long cutoff = System.currentTimeMillis() - CONFIRMATION_TTL_MILLIS;\n        confirmations.entrySet().removeIf(entry -> entry.getValue().createdAt() < cutoff);\n    }\n\n    private Player requirePlayer(CommandSender sender) {\n""",
)
replace_once(
    currency_command,
    """    private static String rootMessage(Throwable failure) {\n        Throwable current = failure;\n        while ((current instanceof java.util.concurrent.CompletionException\n                || current instanceof java.util.concurrent.ExecutionException)\n                && current.getCause() != null) {\n            current = current.getCause();\n        }\n        while (current.getCause() != null && current.getCause() != current) {\n            current = current.getCause();\n        }\n        String message = current.getMessage();\n        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;\n    }\n\n""",
    "",
)

admin_command = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/command/EconomyAdminCommand.java"
replace_all(
    admin_command,
    "rootMessage(failure)",
    "feature.service().userFacingFailure(failure)",
)
replace_once(
    admin_command,
    """    private static String rootMessage(Throwable failure) {\n        Throwable current = failure;\n        while ((current instanceof java.util.concurrent.CompletionException\n                || current instanceof java.util.concurrent.ExecutionException)\n                && current.getCause() != null) {\n            current = current.getCause();\n        }\n        while (current.getCause() != null && current.getCause() != current) {\n            current = current.getCause();\n        }\n        String message = current.getMessage();\n        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;\n    }\n\n""",
    "",
)

economy = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/Economy.java"
replace_once(
    economy,
    """import java.util.List;\nimport java.util.Map;\n""",
    """import java.util.ArrayList;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.UUID;\n""",
)
replace_once(
    economy,
    """    private EconomySettings settings;\n    private EconomyService service;\n    private EconomyMessaging messaging;\n""",
    """    private final List<CurrencyCommand> currencyCommands = new ArrayList<>();\n    private EconomySettings settings;\n    private EconomyService service;\n    private EconomyMessaging messaging;\n    private String messagingStatus = \"disabled\";\n""",
)
replace_once(
    economy,
    """        if (settings.messaging().enabled()) {\n            dataManager.registerRedisMessagingDataAccess(MESSAGING_CONNECTION, settings.messaging().connection())\n                    .ifPresentOrElse(access -> {\n                        messaging = new EconomyMessaging(this, access, settings.messaging().channel());\n                        messaging.start();\n                        service.setMessaging(messaging);\n                    }, () -> getLogger().warning(\n                            \"Economy messaging is unavailable; database correctness remains active.\"\n                    ));\n        }\n\n        for (EconomySettings.Currency currency : settings.currencies().values()) {\n            getLifecycleManager().getCommandManager().registerBrigadierCommand(new CurrencyCommand(this, currency));\n        }\n""",
    """        messagingStatus = \"disabled\";\n        if (settings.messaging().enabled()) {\n            dataManager.registerRedisMessagingDataAccess(MESSAGING_CONNECTION, settings.messaging().connection())\n                    .ifPresentOrElse(access -> {\n                        EconomyMessaging candidate = new EconomyMessaging(\n                                this,\n                                access,\n                                settings.messaging().channel()\n                        );\n                        try {\n                            candidate.start();\n                            messaging = candidate;\n                            service.setMessaging(candidate);\n                            messagingStatus = \"active\";\n                        } catch (RuntimeException failure) {\n                            candidate.close();\n                            messagingStatus = \"unavailable\";\n                            getLogger().warning(\n                                    \"Economy messaging could not start; database correctness remains active: \"\n                                            + failure.getMessage()\n                            );\n                        }\n                    }, () -> {\n                        messagingStatus = \"unavailable\";\n                        getLogger().warning(\n                                \"Economy messaging is unavailable; database correctness remains active.\"\n                        );\n                    });\n        }\n\n        currencyCommands.clear();\n        for (EconomySettings.Currency currency : settings.currencies().values()) {\n            CurrencyCommand command = new CurrencyCommand(this, currency);\n            currencyCommands.add(command);\n            getLifecycleManager().getCommandManager().registerBrigadierCommand(command);\n        }\n""",
)
replace_once(
    economy,
    """    public void disable() {\n        if (placeholder != null) {\n""",
    """    public void disable() {\n        currencyCommands.forEach(CurrencyCommand::close);\n        currencyCommands.clear();\n        if (placeholder != null) {\n""",
)
replace_once(
    economy,
    """        if (messaging != null) {\n            messaging.close();\n            messaging = null;\n        }\n""",
    """        if (messaging != null) {\n            messaging.close();\n            messaging = null;\n        }\n        messagingStatus = \"disabled\";\n""",
)
replace_once(
    economy,
    """    public EconomySettings settings() {\n""",
    """    public void evict(UUID playerUuid) {\n        if (service != null) {\n            service.evict(playerUuid);\n        }\n        currencyCommands.forEach(command -> command.evict(playerUuid));\n    }\n\n    public EconomySettings settings() {\n""",
)
replace_once(
    economy,
    """    public String messagingStatus() {\n        return messaging == null ? \"disabled\" : \"active\";\n    }\n""",
    """    public String messagingStatus() {\n        return messagingStatus;\n    }\n""",
)

listener = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/listener/EconomyPlayerListener.java"
replace_once(
    listener,
    """        feature.service().evict(event.getPlayer().getUniqueId());\n""",
    """        feature.evict(event.getPlayer().getUniqueId());\n""",
)

settings = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/config/EconomySettings.java"
replace_once(
    settings,
    """        Set<String> labels = new LinkedHashSet<>();\n""",
    """        Set<String> labels = new LinkedHashSet<>(Set.of(\"economy\"));\n""",
)
replace_once(
    settings,
    """                    throw new IllegalArgumentException(\"Duplicate economy command label: \" + label);\n""",
    """                    throw new IllegalArgumentException(\n                            \"Reserved or duplicate economy command label: \" + label\n                    );\n""",
)

placeholder = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/placeholder/EconomyPlaceholder.java"
replace_once(placeholder, '        return "1.0.0";\n', '        return "1.1.0";\n')
replace_once(
    placeholder,
    """            case \"payments\" -> feature.service().cachedAccount(player.getUniqueId(), currency.id())\n                    .map(value -> Boolean.toString(value.paymentsEnabled())).orElse(\"true\");\n""",
    """            case \"payments\" -> feature.service().cachedAccount(player.getUniqueId(), currency.id())\n                    .map(value -> Boolean.toString(value.paymentsEnabled()))\n                    .orElse(Boolean.toString(currency.payments().defaultEnabled()));\n            case \"ready\" -> Boolean.toString(balance.isPresent());\n""",
)

vault = "serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/economy/vault/VaultProviderRegistration.java"
write(
    vault,
    '''package nl.hauntedmc.serverfeatures.features.economy.vault;\n\nimport nl.hauntedmc.serverfeatures.features.economy.Economy;\nimport nl.hauntedmc.serverfeatures.features.economy.EconomyVaultIntegration;\nimport nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings.VaultConflictPolicy;\nimport org.bukkit.Bukkit;\nimport org.bukkit.plugin.Plugin;\nimport org.bukkit.plugin.RegisteredServiceProvider;\nimport org.bukkit.plugin.ServicePriority;\nimport org.bukkit.plugin.ServicesManager;\n\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Objects;\n\n/** Registers and cleanly unregisters the optional Vault provider. */\npublic final class VaultProviderRegistration implements EconomyVaultIntegration {\n    private final Economy feature;\n    private final List<DisplacedProvider> displacedProviders = new ArrayList<>();\n    private VaultEconomyProvider provider;\n    private String status = "disabled";\n\n    public VaultProviderRegistration(Economy feature) {\n        this.feature = Objects.requireNonNull(feature, "feature");\n    }\n\n    @Override\n    public void register() {\n        if (!feature.settings().vault().enabled()) {\n            status = "disabled";\n            return;\n        }\n        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {\n            status = "vault-missing";\n            feature.getLogger().warning("Vault integration is enabled, but Vault is not installed.");\n            return;\n        }\n\n        ServicesManager services = Bukkit.getServicesManager();\n        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> existing =\n                services.getRegistration(net.milkbowl.vault.economy.Economy.class);\n        VaultConflictPolicy policy = feature.settings().vault().conflictPolicy();\n        if (existing != null && existing.getProvider() != null) {\n            if (policy == VaultConflictPolicy.FAIL) {\n                throw new IllegalStateException(\n                        "Another Vault economy provider is already active: " + existing.getProvider().getName()\n                );\n            }\n            if (policy == VaultConflictPolicy.SKIP) {\n                status = "skipped:" + existing.getProvider().getName();\n                feature.getLogger().warning(\n                        "Keeping existing Vault economy provider: " + existing.getProvider().getName()\n                );\n                return;\n            }\n            displaceExistingProviders(services);\n        }\n\n        VaultEconomyProvider candidate = new VaultEconomyProvider(\n                feature.service(),\n                feature.settings().vault().primaryCurrency()\n        );\n        services.register(\n                net.milkbowl.vault.economy.Economy.class,\n                candidate,\n                feature.getPlugin(),\n                ServicePriority.Highest\n        );\n        provider = candidate;\n        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> selected =\n                services.getRegistration(net.milkbowl.vault.economy.Economy.class);\n        if (selected == null || selected.getProvider() != candidate) {\n            rollbackRegistration(services);\n            throw new IllegalStateException("ServerFeatures Economy did not become the active Vault provider");\n        }\n        status = "registered:" + feature.settings().vault().primaryCurrency();\n        if (!displacedProviders.isEmpty()) {\n            status += ":replaced=" + displacedProviders.size();\n        }\n    }\n\n    private void displaceExistingProviders(ServicesManager services) {\n        List<RegisteredServiceProvider<net.milkbowl.vault.economy.Economy>> registrations =\n                List.copyOf(services.getRegistrations(net.milkbowl.vault.economy.Economy.class));\n        for (RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration : registrations) {\n            net.milkbowl.vault.economy.Economy existingProvider = registration.getProvider();\n            if (existingProvider == null) {\n                continue;\n            }\n            displacedProviders.add(new DisplacedProvider(\n                    existingProvider,\n                    registration.getPlugin(),\n                    registration.getPriority()\n            ));\n            services.unregister(net.milkbowl.vault.economy.Economy.class, existingProvider);\n            feature.getLogger().warning("Replaced Vault economy provider: " + existingProvider.getName());\n        }\n    }\n\n    private void rollbackRegistration(ServicesManager services) {\n        VaultEconomyProvider current = provider;\n        provider = null;\n        if (current != null) {\n            current.disable();\n            services.unregister(net.milkbowl.vault.economy.Economy.class, current);\n        }\n        restoreDisplacedProviders(services);\n    }\n\n    private void restoreDisplacedProviders(ServicesManager services) {\n        List<RegisteredServiceProvider<net.milkbowl.vault.economy.Economy>> current =\n                List.copyOf(services.getRegistrations(net.milkbowl.vault.economy.Economy.class));\n        for (DisplacedProvider displaced : List.copyOf(displacedProviders)) {\n            Plugin plugin = displaced.plugin();\n            if (!plugin.isEnabled()) {\n                continue;\n            }\n            boolean alreadyRegistered = current.stream()\n                    .anyMatch(registration -> registration.getProvider() == displaced.provider());\n            if (!alreadyRegistered) {\n                services.register(\n                        net.milkbowl.vault.economy.Economy.class,\n                        displaced.provider(),\n                        plugin,\n                        displaced.priority()\n                );\n            }\n        }\n        displacedProviders.clear();\n    }\n\n    @Override\n    public String status() {\n        return status;\n    }\n\n    @Override\n    public void close() {\n        ServicesManager services = Bukkit.getServicesManager();\n        VaultEconomyProvider current = provider;\n        provider = null;\n        if (current != null) {\n            current.disable();\n            services.unregister(net.milkbowl.vault.economy.Economy.class, current);\n        }\n        restoreDisplacedProviders(services);\n        status = "disabled";\n    }\n\n    private record DisplacedProvider(\n            net.milkbowl.vault.economy.Economy provider,\n            Plugin plugin,\n            ServicePriority priority\n    ) {\n    }\n}\n''',
)

settings_test = "serverfeatures-platform-paper/src/test/java/nl/hauntedmc/serverfeatures/features/economy/config/EconomySettingsTest.java"
replace_once(
    settings_test,
    """    @Test\n    void requiresEnabledVaultPrimaryCurrency() {\n""",
    """    @Test\n    void rejectsAdminCommandAsCurrencyRootOrAlias() {\n        EconomySettings.Currency money = currency(\n                \"money\",\n                new EconomyScope(EconomyScopeType.SERVER, \"hauntedmc/server/survival\"),\n                \"economy\"\n        );\n\n        assertThrows(IllegalArgumentException.class, () -> settings(\n                \"survival\",\n                Map.of(\"money\", money),\n                true\n        ));\n    }\n\n    @Test\n    void requiresEnabledVaultPrimaryCurrency() {\n""",
)

transient_test = "serverfeatures-platform-paper/src/test/java/nl/hauntedmc/serverfeatures/features/economy/persistence/EconomyTransientFailureTest.java"
write(
    transient_test,
    '''package nl.hauntedmc.serverfeatures.features.economy.persistence;\n\nimport org.junit.jupiter.api.Test;\n\nimport java.sql.SQLException;\nimport java.sql.SQLRecoverableException;\nimport java.sql.SQLTransientException;\n\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nclass EconomyTransientFailureTest {\n\n    @Test\n    void recognizesSqlTransientAndRecoverableFailures() {\n        assertTrue(EconomyRepository.isTransient(new SQLTransientException("temporary")));\n        assertTrue(EconomyRepository.isTransient(new SQLRecoverableException("connection reset")));\n    }\n\n    @Test\n    void recognizesConnectionRollbackAndMysqlConcurrencyCodes() {\n        assertTrue(EconomyRepository.isTransient(new SQLException("connection", "08006", 0)));\n        assertTrue(EconomyRepository.isTransient(new SQLException("rollback", "40001", 0)));\n        assertTrue(EconomyRepository.isTransient(new SQLException("duplicate", "23000", 1062)));\n        assertTrue(EconomyRepository.isTransient(new SQLException("lock wait", "HY000", 1205)));\n        assertTrue(EconomyRepository.isTransient(new SQLException("deadlock", "40001", 1213)));\n    }\n\n    @Test\n    void rejectsPermanentSqlAndValidationFailures() {\n        assertFalse(EconomyRepository.isTransient(new SQLException("syntax", "42000", 1064)));\n        assertFalse(EconomyRepository.isTransient(new IllegalArgumentException("invalid request")));\n    }\n}\n''',
)

docs = "docs/features/economy.md"
replace_once(
    docs,
    """- `REPLACE`: explicitly register ServerFeatures Economy at highest priority.\n""",
    """- `REPLACE`: unregister all existing Vault economy registrations, activate ServerFeatures Economy, verify that Vault selected it, and restore displaced providers when Economy shuts down.\n""",
)
replace_once(
    docs,
    """%economy_money_payments%\n%economy_primary_balance%\n""",
    """%economy_money_payments%\n%economy_money_ready%\n%economy_primary_balance%\n""",
)
replace_once(
    docs,
    """Placeholder evaluation never blocks the Paper thread. Online-player cache entries are refreshed from authoritative MySQL periodically and after network invalidations.\n""",
    """Placeholder evaluation never blocks the Paper thread. Online-player cache entries are refreshed from authoritative MySQL periodically and after network invalidations. The `ready` suffix reports whether an authoritative account snapshot is cached. Placeholders are display-only and must never be used to authorize purchases, rewards, withdrawals or other monetary decisions.\n""",
)

checklist = "docs/features/economy-deployment-checklist.md"
replace_once(
    checklist,
    """- Every participating instance must run the same ServerFeatures build. Do not operate mixed Economy schema or messaging versions during rollout.\n""",
    """- Every participating instance must run the same ServerFeatures build. Do not operate mixed Economy schema or messaging versions during rollout.\n- Keep the MySQL host and every Paper host synchronized with reliable NTP. Cooldown, daily-limit and audit timestamps depend on a consistent network clock.\n""",
)
replace_once(
    checklist,
    """- Never repair balances by editing cache or Redis data.\n""",
    """- Never repair balances by editing cache or Redis data.\n- Never use PlaceholderAPI values to authorize monetary behavior; they are cache-only display values.\n""",
)

print("Economy production hardening applied")
