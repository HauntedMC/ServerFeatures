package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveTableMappingTest {
    @Test
    void everyGraveyardTableUsesPlayerPrefix() {
        List<Class<?>> entities = List.of(
                GraveMetadataEntity.class,
                GravePayloadEntity.class,
                GraveAuditEntity.class,
                GraveLeaseEntity.class
        );

        for (Class<?> entity : entities) {
            Table table = entity.getAnnotation(Table.class);
            assertTrue(table.name().startsWith("player_"), entity.getSimpleName());
        }
    }
}
