package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyCommandPermissionsTest {

    @Test
    void balanceOthersPermissionGrantsTheDedicatedReadActionWithoutGrantingSelfBalance() {
        CommandSender sender = senderWith("serverfeatures.feature.economy.currency.money.balance.others");

        assertTrue(EconomyCommandPermissions.canViewAnyBalance(sender, "money"));
        assertFalse(EconomyCommandPermissions.canViewOwnBalance(sender, "money"));
    }

    @Test
    void broadAdminPermissionGrantsEveryAdministrativeAction() {
        CommandSender sender = senderWith("serverfeatures.feature.economy.admin");

        assertTrue(EconomyCommandPermissions.hasAnyAdminPermission(sender));
        assertTrue(EconomyCommandPermissions.adminAction(sender, "verify"));
        assertTrue(EconomyCommandPermissions.adminAction(sender, "freeze"));
    }

    @Test
    void balanceOthersDoesNotExposeACommandWhenTheConfigurationDisablesIt() {
        CommandSender sender = senderWith("serverfeatures.feature.economy.currency.money.balance.others");
        EconomySettings.Currency currency = mock(EconomySettings.Currency.class);
        EconomySettings.Commands commands = mock(EconomySettings.Commands.class);
        when(currency.id()).thenReturn("money");
        when(currency.commands()).thenReturn(commands);
        when(commands.balance()).thenReturn(true);
        when(commands.balanceOthers()).thenReturn(false);

        assertFalse(EconomyCommandPermissions.canUseAnyCurrencyCommand(sender, currency));
    }

    private CommandSender senderWith(String grantedPermission) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(grantedPermission)).thenReturn(true);
        return sender;
    }
}
