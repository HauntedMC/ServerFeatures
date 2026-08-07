package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.bukkit.command.CommandSender;

/**
 * Centralizes the permission contract for Economy's dynamically registered commands.
 *
 * <p>Currency-specific permissions allow a role to be scoped to one currency, while the
 * legacy global permissions continue to grant the same action for every currency. The explicit
 * broad administrator permission is useful for server owners and avoids relying on permission
 * plugin-specific wildcard behavior.</p>
 */
final class EconomyCommandPermissions {
    private static final String ECONOMY_PERMISSION = "serverfeatures.feature.economy";
    private static final String ADMIN_PERMISSION = ECONOMY_PERMISSION + ".admin";

    private EconomyCommandPermissions() {
    }

    static boolean playerAction(CommandSender sender, String currencyId, String action) {
        return sender.hasPermission(currencyPermission(currencyId, action))
                || sender.hasPermission(ECONOMY_PERMISSION + "." + action)
                || sender.hasPermission(ECONOMY_PERMISSION + ".currency." + currencyId)
                || sender.hasPermission(ECONOMY_PERMISSION);
    }

    static boolean canViewOwnBalance(CommandSender sender, String currencyId) {
        return playerAction(sender, currencyId, "balance");
    }

    static boolean canViewAnyBalance(CommandSender sender, String currencyId) {
        return playerAction(sender, currencyId, "balance.others");
    }

    static boolean canUseAnyCurrencyCommand(CommandSender sender, EconomySettings.Currency currency) {
        EconomySettings.Commands commands = currency.commands();
        return commands.balance() && (canViewOwnBalance(sender, currency.id())
                || commands.balanceOthers() && canViewAnyBalance(sender, currency.id()))
                || commands.pay() && playerAction(sender, currency.id(), "pay")
                || commands.paytoggle() && playerAction(sender, currency.id(), "paytoggle")
                || commands.history() && playerAction(sender, currency.id(), "history")
                || commands.top() && playerAction(sender, currency.id(), "top");
    }

    static boolean adminAction(CommandSender sender, String action) {
        return sender.hasPermission(ADMIN_PERMISSION + "." + action)
                || sender.hasPermission(ADMIN_PERMISSION)
                || sender.hasPermission(ADMIN_PERMISSION + ".*");
    }

    /**
     * Returns whether a sender may access the shared-definition command branch.
     *
     * <p>Reading definitions and writing a configuration scaffold deliberately use separate
     * permissions. This keeps a support role from being able to change a server's configuration
     * merely because it needs to inspect the shared monetary policy.</p>
     */
    static boolean canUseDefinitionCommands(CommandSender sender) {
        return adminAction(sender, "definitions") || adminAction(sender, "definitions.import");
    }

    static boolean canInspectDefinitions(CommandSender sender) {
        return adminAction(sender, "definitions");
    }

    static boolean canImportDefinitions(CommandSender sender) {
        return adminAction(sender, "definitions.import");
    }

    static boolean hasAnyAdminPermission(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(ADMIN_PERMISSION + ".*")
                || java.util.stream.Stream.of("status", "balance", "add", "remove", "set", "payments", "freeze", "history", "verify", "definitions", "definitions.import", "maintenance")
                .anyMatch(action -> adminAction(sender, action));
    }

    private static String currencyPermission(String currencyId, String action) {
        return ECONOMY_PERMISSION + ".currency." + currencyId + "." + action;
    }
}
