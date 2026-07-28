package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvToolsPermissionsTest {

    @Test
    void permissionsAreScopedToTheCommandAndAction() {
        assertEquals(
                "serverfeatures.feature.invtools.command.invsee.inspect",
                InvToolsService.inspectPermission(InventoryKind.PLAYER)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.invsee.edit",
                InvToolsService.editPermission(InventoryKind.PLAYER)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.endersee.inspect",
                InvToolsService.inspectPermission(InventoryKind.ENDER_CHEST)
        );
        assertEquals(
                "serverfeatures.feature.invtools.command.endersee.edit",
                InvToolsService.editPermission(InventoryKind.ENDER_CHEST)
        );
    }
}
