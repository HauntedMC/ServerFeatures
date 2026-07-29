package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvToolsPermissionsTest {

    @Test
    void permissionsAreScopedToTheCommandAndAction() {
        assertEquals(
                "serverfeatures.feature.invtools.command.inventory.open.inspect",
                InvToolsService.openInspectPermission(InventoryKind.PLAYER)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.inventory.open.edit",
                InvToolsService.openEditPermission(InventoryKind.PLAYER)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.inventory.clear",
                InvToolsService.clearPermission(InventoryKind.PLAYER)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.enderchest.open.inspect",
                InvToolsService.openInspectPermission(InventoryKind.ENDER_CHEST)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.enderchest.open.edit",
                InvToolsService.openEditPermission(InventoryKind.ENDER_CHEST)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.enderchest.clear",
                InvToolsService.clearPermission(InventoryKind.ENDER_CHEST)
        );
    }
}
