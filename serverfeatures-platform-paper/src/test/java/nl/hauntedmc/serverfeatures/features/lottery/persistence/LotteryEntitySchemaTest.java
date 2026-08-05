package nl.hauntedmc.serverfeatures.features.lottery.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryEntryEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPayoutEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPlayerStatsEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryRoundEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotteryEntitySchemaTest {

    @Test
    void usesFourNormalOrmEntities() throws NoSuchFieldException {
        List<Class<?>> entities = List.of(
                LotteryRoundEntity.class,
                LotteryEntryEntity.class,
                LotteryPayoutEntity.class,
                LotteryPlayerStatsEntity.class
        );
        assertEquals(4, entities.size());
        for (Class<?> entity : entities) {
            assertNotNull(entity.getAnnotation(Entity.class));
            assertNotNull(entity.getAnnotation(Table.class));
        }

        Table roundTable = LotteryRoundEntity.class.getAnnotation(Table.class);
        assertTrue(List.of(roundTable.uniqueConstraints()).stream()
                .anyMatch(constraint -> List.of(constraint.columnNames()).contains("active_key")));
        assertNotNull(LotteryRoundEntity.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(LotteryPlayerStatsEntity.class.getDeclaredField("version").getAnnotation(Version.class));
    }
}
