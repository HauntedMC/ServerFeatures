package nl.hauntedmc.serverfeatures.features.invtools.gui;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerData;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataRevision;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class InvToolsViewOfflineCursorTest {

    private static final PlayerDataRevision REVISION = new PlayerDataRevision("0".repeat(64));

    @Test
    void programmaticSaveReturnsViewerCursorToItsOriginalSlot() {
        ItemStack carried = item(Material.DIAMOND, 4);
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[4] = carried;

        withView(InventorySnapshot.empty(), storage, fixture -> {
            ItemStack[] before = fixture.storage();
            OfflineCursorTransaction.Plan pickup = fixture.view().planOfflineCursor(
                    OfflineCursorTransaction.Side.VIEWER,
                    4,
                    InventoryAction.PICKUP_ALL,
                    before[4]
            ).orElseThrow();
            ItemStack[] changed = PlayerStorageTransfer.copyStorage(before);
            changed[4] = null;

            assertTrue(fixture.view().applyOfflineCursorMutation(
                    pickup,
                    fixture.view().snapshot(),
                    before,
                    changed
            ));
            assertNull(fixture.storage()[4]);

            InvToolsView.OfflineSavePlan savePlan = fixture.view().beginOfflineSave();

            assertTrue(fixture.storage()[4].isSimilar(carried));
            assertEquals(4, fixture.storage()[4].getAmount());
            assertNull(fixture.view().cursor());
            assertFalse(savePlan.dirty());
        });
    }

    @Test
    void targetArmorCursorReturnsToItsExactEquipmentSlotBeforeSave() {
        ItemStack helmet = item(Material.DIAMOND_HELMET);
        InventorySnapshot target = InventorySnapshot.empty().withBackingSlot(
                InventoryKind.PLAYER,
                InventorySnapshot.HELMET_SLOT,
                helmet
        );

        withView(target, new ItemStack[InventorySnapshot.STORAGE_SIZE], fixture -> {
            ItemStack[] viewerStorage = fixture.storage();
            OfflineCursorTransaction.Plan pickup = fixture.view().planOfflineCursor(
                    OfflineCursorTransaction.Side.TARGET,
                    InventorySnapshot.HELMET_SLOT,
                    InventoryAction.PICKUP_ALL,
                    helmet
            ).orElseThrow();
            InventorySnapshot changed = target.withBackingSlot(
                    InventoryKind.PLAYER,
                    InventorySnapshot.HELMET_SLOT,
                    null
            );

            assertTrue(fixture.view().applyOfflineCursorMutation(
                    pickup,
                    changed,
                    viewerStorage,
                    viewerStorage
            ));

            InvToolsView.OfflineSavePlan savePlan = fixture.view().beginOfflineSave();

            assertTrue(savePlan.changedSnapshot().helmet().isSimilar(helmet));
            assertNull(savePlan.changedSnapshot().itemAt(InventoryKind.PLAYER, 0));
            assertNull(fixture.view().cursor());
        });
    }

    @Test
    void discardReturnsAViewerOwnedCursorBeforeClosing() {
        ItemStack carried = item(Material.EMERALD, 2);
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[7] = carried;

        withView(InventorySnapshot.empty(), storage, fixture -> {
            ItemStack[] before = fixture.storage();
            OfflineCursorTransaction.Plan pickup = fixture.view().planOfflineCursor(
                    OfflineCursorTransaction.Side.VIEWER,
                    7,
                    InventoryAction.PICKUP_ALL,
                    before[7]
            ).orElseThrow();
            ItemStack[] changed = PlayerStorageTransfer.copyStorage(before);
            changed[7] = null;
            assertTrue(fixture.view().applyOfflineCursorMutation(
                    pickup,
                    fixture.view().snapshot(),
                    before,
                    changed
            ));

            fixture.view().closeWithoutSaving();

            assertTrue(fixture.view().isClosed());
            assertTrue(fixture.storage()[7].isSimilar(carried));
            assertNull(fixture.view().cursor());
        });
    }

    @Test
    void failedDirectCrossTransferRestoresTheStaffInventory() {
        ItemStack carried = item(Material.DIAMOND, 3);
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[5] = carried;

        withView(InventorySnapshot.empty(), storage, fixture -> {
            ItemStack[] beforePickup = fixture.storage();
            OfflineCursorTransaction.Plan pickup = fixture.view().planOfflineCursor(
                    OfflineCursorTransaction.Side.VIEWER,
                    5,
                    InventoryAction.PICKUP_ALL,
                    beforePickup[5]
            ).orElseThrow();
            ItemStack[] afterPickup = PlayerStorageTransfer.copyStorage(beforePickup);
            afterPickup[5] = null;
            assertTrue(fixture.view().applyOfflineCursorMutation(
                    pickup,
                    fixture.view().snapshot(),
                    beforePickup,
                    afterPickup
            ));

            InventorySnapshot beforePlacement = fixture.view().snapshot();
            OfflineCursorTransaction.Plan placement = fixture.view().planOfflineCursor(
                    OfflineCursorTransaction.Side.TARGET,
                    9,
                    InventoryAction.PLACE_ALL,
                    null
            ).orElseThrow();
            InventorySnapshot afterPlacement = beforePlacement.withBackingSlot(
                    InventoryKind.PLAYER,
                    9,
                    placement.result().slotItem()
            );
            ItemStack[] unchangedViewer = fixture.storage();
            assertTrue(fixture.view().applyOfflineCursorMutation(
                    placement,
                    afterPlacement,
                    unchangedViewer,
                    unchangedViewer
            ));
            assertNull(fixture.storage()[5]);

            fixture.view().beginOfflineSave();
            fixture.view().finishSave(InvToolsView.SaveResult.FAILED);

            assertTrue(fixture.storage()[9].isSimilar(carried));
            assertTrue(fixture.view().isClosed());
        });
    }

    private static void withView(
            InventorySnapshot targetSnapshot,
            ItemStack[] initialStorage,
            Consumer<Fixture> assertion
    ) {
        Player viewer = mock(Player.class);
        PlayerInventory viewerInventory = mock(PlayerInventory.class);
        Inventory topInventory = mock(Inventory.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder message = mock(
                LocalizationHandler.MessageBuilder.class
        );
        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AtomicReference<ItemStack[]> storage = new AtomicReference<>(
                PlayerStorageTransfer.copyStorage(initialStorage)
        );

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.getName()).thenReturn("Moderator");
        when(viewer.getInventory()).thenReturn(viewerInventory);
        when(viewerInventory.getStorageContents()).thenAnswer(ignored ->
                PlayerStorageTransfer.copyStorage(storage.get())
        );
        doAnswer(invocation -> {
            storage.set(PlayerStorageTransfer.copyStorage(invocation.getArgument(0)));
            return null;
        }).when(viewerInventory).setStorageContents(any(ItemStack[].class));
        when(topInventory.getSize()).thenReturn(54);
        when(localization.getMessage(anyString())).thenReturn(message);
        when(message.forAudience(viewer)).thenReturn(message);
        when(message.build()).thenReturn(Component.empty());

        OfflinePlayerData original = new OfflinePlayerData(
                targetId,
                targetSnapshot,
                REVISION
        );
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedConstruction<ItemStack> ignored = mockConstruction(
                     ItemStack.class,
                     (constructed, context) -> when(constructed.getItemMeta()).thenReturn(itemMeta)
             )) {
            bukkit.when(() -> Bukkit.createInventory(
                    any(InventoryHolder.class),
                    anyInt(),
                    any(Component.class)
            )).thenReturn(topInventory);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            InvToolsView view = new InvToolsView(
                    localization,
                    viewer,
                    targetId,
                    "Target",
                    InventoryKind.PLAYER,
                    false,
                    true,
                    targetSnapshot,
                    original
            );
            assertion.accept(new Fixture(view, storage));
        }
    }

    private record Fixture(
            InvToolsView view,
            AtomicReference<ItemStack[]> storageReference
    ) {
        private Fixture {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(storageReference, "storageReference");
        }

        private ItemStack[] storage() {
            return PlayerStorageTransfer.copyStorage(storageReference.get());
        }
    }
}
