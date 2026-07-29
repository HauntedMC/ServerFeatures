package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerData;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerDataStore;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataRevision;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;

class InvToolsServiceTest {

    @Test
    void onlineMutationRequiresTheClickedSlotToStillMatchTheRenderedSnapshot() {
        InventorySnapshot rendered = InventorySnapshot.empty()
                .withBackingSlot(InventoryKind.PLAYER, 12, item(Material.DIAMOND, 3));
        InventorySnapshot sameSlot = rendered
                .withBackingSlot(InventoryKind.PLAYER, 13, item(Material.EMERALD));
        InventorySnapshot changedSlot = rendered
                .withBackingSlot(InventoryKind.PLAYER, 12, item(Material.DIAMOND, 2));

        assertTrue(InvToolsService.onlineSlotMatches(
                rendered,
                sameSlot,
                InventoryKind.PLAYER,
                12
        ));
        assertFalse(InvToolsService.onlineSlotMatches(
                rendered,
                changedSlot,
                InventoryKind.PLAYER,
                12
        ));
    }

    @Test
    void retriesBrieflyForPlayerdataCreatedDuringLogout() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerData expected = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                new PlayerDataRevision("0".repeat(64))
        );
        AtomicInteger availabilityChecks = new AtomicInteger();
        AtomicInteger retryDelays = new AtomicInteger();
        OfflinePlayerDataStore store = new OfflinePlayerDataStore() {
            @Override
            public Optional<OfflinePlayerData> loadIfPresent(
                    UUID checkedPlayerId,
                    String playerName,
                    boolean onlineMode
            ) {
                assertEquals(playerId, checkedPlayerId);
                assertEquals("Target", playerName);
                assertTrue(onlineMode);
                return availabilityChecks.incrementAndGet() == 2
                        ? Optional.of(expected)
                        : Optional.empty();
            }

            @Override
            public boolean hasPlayerData(UUID checkedPlayerId) {
                throw new UnsupportedOperationException("Not needed by this test");
            }

            @Override
            public OfflinePlayerData load(UUID loadedPlayerId) {
                assertEquals(playerId, loadedPlayerId);
                return expected;
            }

            @Override
            public void save(
                    OfflinePlayerData original,
                    InventoryKind kind,
                    InventorySnapshot changedSnapshot
            ) {
                throw new UnsupportedOperationException("Not needed by this test");
            }
        };

        OfflinePlayerData actual = InvToolsService.loadOffline(
                store,
                playerId,
                "Target",
                true,
                3,
                retryDelays::incrementAndGet
        );

        assertSame(expected, actual);
        assertEquals(2, availabilityChecks.get());
        assertEquals(1, retryDelays.get());
    }
}
