package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TopEntry;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransferReceipt;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns read-model queries and integrity diagnostics; it never mutates monetary state. */
final class EconomyQueryStore {
    Optional<TransferReceipt> transferReceipt(Session session, UUID operationId) {
        EconomyTransactionEntity transaction = session.createSelectionQuery(
                        "from EconomyTransactionEntity where operationId = :operationId", EconomyTransactionEntity.class)
                .setParameter("operationId", operationId.toString()).setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
        if (transaction == null || !TransactionType.TRANSFER.name().equals(transaction.getTransactionType())) {
            return Optional.empty();
        }
        List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                        "from EconomyTransactionEntryEntity where transactionId = :transactionId order by id asc",
                        EconomyTransactionEntryEntity.class)
                .setParameter("transactionId", transaction.getId()).getResultList();
        EconomyTransactionEntryEntity senderEntry = role(entries, "SENDER");
        EconomyTransactionEntryEntity recipientEntry = role(entries, "RECIPIENT");
        if (entries.size() != 2 || senderEntry == null || recipientEntry == null
                || senderEntry.getAccountId().equals(recipientEntry.getAccountId())
                || recipientEntry.getDelta().signum() <= 0
                || senderEntry.getDelta().negate().compareTo(recipientEntry.getDelta()) != 0
                || senderEntry.getBalanceBefore().add(senderEntry.getDelta()).compareTo(senderEntry.getBalanceAfter()) != 0
                || recipientEntry.getBalanceBefore().add(recipientEntry.getDelta()).compareTo(recipientEntry.getBalanceAfter()) != 0) {
            throw new IllegalStateException("Economy transfer " + operationId + " has invalid journal entries");
        }
        EconomyBalanceEntity sender = session.find(EconomyBalanceEntity.class, senderEntry.getAccountId());
        EconomyBalanceEntity recipient = session.find(EconomyBalanceEntity.class, recipientEntry.getAccountId());
        if (sender == null || recipient == null || sender.getPlayerId() != senderEntry.getPlayerId()
                || recipient.getPlayerId() != recipientEntry.getPlayerId()
                || !transaction.getCurrencyId().equals(sender.getCurrencyId())
                || !transaction.getCurrencyId().equals(recipient.getCurrencyId())
                || !transaction.getScopeKey().equals(sender.getScopeKey())
                || !transaction.getScopeKey().equals(recipient.getScopeKey())) {
            throw new IllegalStateException("Economy transfer " + operationId + " references an inconsistent account");
        }
        return Optional.of(new TransferReceipt(operationId, EconomyPersistenceValues.identity(sender),
                EconomyPersistenceValues.identity(recipient), transaction.getCurrencyId(), transaction.getScopeKey(),
                recipientEntry.getDelta(), recipientEntry.getBalanceAfter()));
    }

    HistoryPage history(Session session, EconomyBalanceEntity account, int page, int pageSize) {
        List<EconomyTransactionEntryEntity> entries = session.createSelectionQuery(
                        "from EconomyTransactionEntryEntity where accountId = :accountId order by transactionId desc",
                        EconomyTransactionEntryEntity.class)
                .setParameter("accountId", account.getId()).setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize + 1).getResultList();
        boolean hasMore = entries.size() > pageSize;
        if (hasMore) entries = new ArrayList<>(entries.subList(0, pageSize));
        List<HistoryItem> result = new ArrayList<>();
        for (EconomyTransactionEntryEntity entry : entries) {
            EconomyTransactionEntity transaction = session.find(EconomyTransactionEntity.class, entry.getTransactionId());
            if (transaction != null) {
                result.add(new HistoryItem(transaction.getId(), UUID.fromString(transaction.getOperationId()),
                        transaction.getTransactionType(), entry.getDelta(), entry.getBalanceAfter(),
                        transaction.getActorName(), transaction.getReason(), transaction.getCreatedAt()));
            }
        }
        return new HistoryPage(result, page, hasMore);
    }

    List<TopEntry> top(Session session, EconomySettings.Currency currency, int offset, int limit) {
        return session.createSelectionQuery(
                        "from EconomyBalanceEntity where currencyId = :currency and scopeKey = :scope "
                                + "order by balance desc, playerId asc", EconomyBalanceEntity.class)
                .setParameter("currency", currency.id()).setParameter("scope", currency.scope().key())
                .setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(entity -> new TopEntry(entity.getPlayerId(), UUID.fromString(entity.getPlayerUuid()),
                        entity.getPlayerName(), entity.getBalance())).toList();
    }

    VerificationReport verify(Session session) {
        long accounts = count(session, "select count(*) from EconomyBalanceEntity");
        long transactions = count(session, "select count(*) from EconomyTransactionEntity");
        long orphanSettings = count(session, "select count(*) from EconomyPlayerSettingsEntity s where not exists "
                + "(select 1 from EconomyBalanceEntity b where b.id = s.accountId)");
        long transactionsWithoutEntries = count(session, "select count(*) from EconomyTransactionEntity t where not exists "
                + "(select 1 from EconomyTransactionEntryEntity e where e.transactionId = t.id)");
        long invalidEntries = count(session, "select count(*) from EconomyTransactionEntryEntity e "
                + "where e.accountKind = :player and (e.balanceBefore is null or e.balanceAfter is null "
                + "or e.balanceBefore + e.delta <> e.balanceAfter)", "player", "PLAYER");
        long invalidTransactions = session.createSelectionQuery(
                        "select count(*) from EconomyTransactionEntity t where "
                                + "(t.transactionType = :transfer and ((select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 2 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :sender) <> 1 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :recipient) <> 1 "
                                + "or (select count(distinct e.accountId) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 2 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :sender and e.delta < 0) <> 1 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :recipient and e.delta > 0) <> 1 "
                                + "or (select sum(e.delta) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 0)) "
                                + "or (t.transactionType in (:deposit, :withdraw, :set, :created) and ((select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 2 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :target and e.accountKind = :player) <> 1 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :system and e.accountKind = :system) <> 1 "
                                + "or (select sum(e.delta) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 0)) "
                                + "or (t.transactionType not in (:transfer, :deposit, :withdraw, :set, :created) and ((select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id) <> 1 "
                                + "or (select count(*) from EconomyTransactionEntryEntity e where e.transactionId = t.id and e.entryRole = :target and e.accountKind = :player and e.delta = 0) <> 1))",
                        Long.class).setParameter("transfer", TransactionType.TRANSFER.name())
                .setParameter("deposit", TransactionType.DEPOSIT.name())
                .setParameter("withdraw", TransactionType.WITHDRAW.name())
                .setParameter("set", TransactionType.SET.name())
                .setParameter("created", TransactionType.ACCOUNT_CREATED.name())
                .setParameter("sender", "SENDER").setParameter("recipient", "RECIPIENT")
                .setParameter("target", "TARGET").setParameter("system", "SYSTEM")
                .setParameter("player", "PLAYER").getSingleResult();
        long orphanEntries = count(session, "select count(*) from EconomyTransactionEntryEntity e where not exists "
                + "(select 1 from EconomyTransactionEntity t where t.id = e.transactionId) or (e.accountKind = :player and not exists "
                + "(select 1 from EconomyBalanceEntity b where b.id = e.accountId))", "player", "PLAYER");
        long identityMismatches = count(session, "select count(*) from EconomyBalanceEntity b where not exists "
                + "(select 1 from EconomyPlayerIdentityEntity i where i.playerId = b.playerId and i.playerUuid = b.playerUuid)");
        long entryAccountMismatches = count(session, "select count(*) from EconomyTransactionEntryEntity e, "
                + "EconomyTransactionEntity t, EconomyBalanceEntity b where e.accountKind = :player and t.id = e.transactionId and b.id = e.accountId "
                + "and (e.playerId <> b.playerId or t.currencyId <> b.currencyId or t.scopeKey <> b.scopeKey)", "player", "PLAYER");
        long accountsWithoutEntries = count(session, "select count(*) from EconomyBalanceEntity b where not exists "
                + "(select 1 from EconomyTransactionEntryEntity e where e.accountKind = :player and e.accountId = b.id)", "player", "PLAYER");
        long balanceJournalMismatches = count(session, "select count(*) from EconomyBalanceEntity b where exists "
                + "(select 1 from EconomyTransactionEntryEntity e where e.accountKind = :player and e.accountId = b.id and e.transactionId = "
                + "(select max(latest.transactionId) from EconomyTransactionEntryEntity latest where latest.accountId = b.id) "
                + "and e.balanceAfter <> b.balance)", "player", "PLAYER");
        long continuityErrors = count(session, "select count(*) from EconomyTransactionEntryEntity e where exists "
                + "(select 1 from EconomyTransactionEntryEntity previous where e.accountKind = :player and previous.accountKind = :player and previous.accountId = e.accountId "
                + "and previous.transactionId = (select max(candidate.transactionId) from EconomyTransactionEntryEntity candidate "
                + "where candidate.accountKind = :player and candidate.accountId = e.accountId and candidate.transactionId < e.transactionId) "
                + "and previous.balanceAfter <> e.balanceBefore)", "player", "PLAYER");
        long invalidBalances = count(session, "select count(*) from EconomyBalanceEntity b where not exists "
                + "(select 1 from EconomyCurrencyDefinitionEntity d where d.currencyId = b.currencyId and d.scopeKey = b.scopeKey) "
                + "or exists (select 1 from EconomyCurrencyDefinitionEntity d where d.currencyId = b.currencyId "
                + "and d.scopeKey = b.scopeKey and (b.balance < d.minimumBalance or b.balance > d.maximumBalance))");
        return new VerificationReport(accounts, transactions, invalidBalances, invalidEntries, invalidTransactions,
                orphanSettings, orphanEntries, identityMismatches, entryAccountMismatches, accountsWithoutEntries,
                transactionsWithoutEntries, balanceJournalMismatches, continuityErrors);
    }

    private static EconomyTransactionEntryEntity role(List<EconomyTransactionEntryEntity> entries, String role) {
        return entries.stream().filter(entry -> role.equals(entry.getEntryRole())).findFirst().orElse(null);
    }

    private static long count(Session session, String query) {
        return session.createSelectionQuery(query, Long.class).getSingleResult();
    }

    private static long count(Session session, String query, String parameter, String value) {
        return session.createSelectionQuery(query, Long.class).setParameter(parameter, value).getSingleResult();
    }
}
