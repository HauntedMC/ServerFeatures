package nl.hauntedmc.serverfeatures.features.economy.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyBalanceEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyDefinitionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyCurrencyFamilyEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyDailyUsageEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerIdentityEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyPlayerSettingsEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyEntitySchemaTest {

    @Test
    void registersNormalOrmEntitiesAndAccountUniqueness() throws NoSuchFieldException {
        List<Class<?>> entities = List.of(
                EconomyCurrencyFamilyEntity.class,
                EconomyCurrencyDefinitionEntity.class,
                EconomyPlayerIdentityEntity.class,
                EconomyBalanceEntity.class,
                EconomyPlayerSettingsEntity.class,
                EconomyTransactionEntity.class,
                EconomyTransactionEntryEntity.class,
                EconomyDailyUsageEntity.class
        );
        for (Class<?> entity : entities) {
            assertNotNull(entity.getAnnotation(Entity.class));
            assertNotNull(entity.getAnnotation(Table.class));
        }
        Table family = EconomyCurrencyFamilyEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(family.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("network_key", "currency_id"))
        ));
        Table identity = EconomyPlayerIdentityEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(identity.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).contains("player_uuid")
        ));
        Table balance = EconomyBalanceEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(balance.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("player_id", "currency_id", "scope_key"))
        ));
        assertTrue(List.of(balance.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("player_uuid", "currency_id", "scope_key"))
        ));
        Table definition = EconomyCurrencyDefinitionEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(definition.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("currency_id", "scope_key"))
        ));
        Table transaction = EconomyTransactionEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(transaction.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("source", "idempotency_key_hash"))
        ));
        Table entry = EconomyTransactionEntryEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(entry.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("transaction_id", "entry_role"))
        ));
        assertTrue(List.of(entry.uniqueConstraints()).stream().anyMatch(constraint ->
                List.of(constraint.columnNames()).containsAll(List.of("transaction_id", "account_id"))
        ));
        assertNotNull(EconomyPlayerIdentityEntity.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(EconomyBalanceEntity.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(EconomyPlayerSettingsEntity.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(EconomyPlayerSettingsEntity.class.getDeclaredField("lastPaymentAt"));
        assertNotNull(EconomyTransactionEntity.class.getDeclaredField("idempotencyKeyHash"));
        assertNotNull(EconomyTransactionEntity.class.getDeclaredField("requestFingerprint"));
    }
}
